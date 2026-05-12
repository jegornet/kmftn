package net.jegor.kmftn.bso

import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import net.jegor.kmftn.base.FtnAddr
import net.jegor.kmftn.base.FtnFlavor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class BsoOutbound(
    public val rootPath: Path,
    public val defaultZone: Short
) {
    private val locks = mutableMapOf<FtnAddr, Mutex>()
    private val globalLock = Mutex()

    private suspend fun getLock(addr: FtnAddr): Mutex = globalLock.withLock {
        locks.getOrPut(addr) { Mutex() }
    }

    /**
     * Resolves the directory for a given address.
     */
    public fun getDirectoryForAddr(addr: FtnAddr): Path {
        val zoneDir = if (addr.zone == defaultZone) {
            rootPath
        } else {
            // BSO uses .zzz or .zzzz (if > 4095).
            // FTS-5005 says "zone number expressed as 3 hexadecimal digits" 
            // "If the zone number > 4095 then 4 hexadecimal digits are used"
            val hexString = if (addr.zone > 0xFFF) {
                addr.zone.toInt().toString(16).padStart(4, '0')
            } else {
                addr.zone.toInt().toString(16).padStart(3, '0')
            }
            Path("$rootPath.$hexString")
        }

        return if (addr.point > 0) {
            val nodeName = getNodeFileName(addr)
            Path(zoneDir, "$nodeName.pnt")
        } else {
            zoneDir
        }
    }

    private fun getNodeFileName(addr: FtnAddr): String {
        val netHex = addr.net.toInt().and(0xFFFF).toString(16).padStart(4, '0')
        val nodeHex = addr.node.toInt().and(0xFFFF).toString(16).padStart(4, '0')
        return netHex + nodeHex
    }

    private fun getPointFileName(addr: FtnAddr): String {
        return addr.point.toInt().and(0xFFFF).toString(16).padStart(8, '0')
    }

    private fun getBaseFileName(addr: FtnAddr): String {
        return if (addr.point > 0) getPointFileName(addr) else getNodeFileName(addr)
    }

    /**
     * Scans the outbound for links.
     */
    public fun listLinks(): List<FtnAddr> {
        val rootParent = rootPath.parent ?: Path(".")
        val rootName = rootPath.name
        val addrs = mutableSetOf<FtnAddr>()

        if (!SystemFileSystem.exists(rootParent)) return emptyList()

        SystemFileSystem.list(rootParent).forEach { path ->
            val name = path.name
            if (name == rootName || (name.startsWith("$rootName.") && name.length > rootName.length + 1)) {
                val zone = if (name == rootName) {
                    defaultZone
                } else {
                    val hex = name.substring(rootName.length + 1)
                    hex.toIntOrNull(16)?.toShort() ?: return@forEach
                }
                scanZoneDirectory(path, zone, addrs)
            }
        }

        return addrs.toList().sortedBy { it.toString() }
    }

    private fun scanZoneDirectory(dir: Path, zone: Short, addrs: MutableSet<FtnAddr>) {
        if (!SystemFileSystem.exists(dir)) return
        SystemFileSystem.list(dir).forEach { path ->
            val name = path.name
            if (name.length >= 8 && name.substring(0, 8)
                    .all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            ) {
                if (name.endsWith(".pnt", ignoreCase = true)) {
                    // It's a point directory
                    val netNodeHex = name.substring(0, 8)
                    val net = netNodeHex.substring(0, 4).toInt(16).toShort()
                    val node = netNodeHex.substring(4, 8).toInt(16).toShort()
                    scanPointDirectory(path, zone, net, node, addrs)
                } else if (name.length == 8 || (name.length == 12 && name[8] == '.')) {
                    val net = name.substring(0, 4).toInt(16).toShort()
                    val node = name.substring(4, 8).toInt(16).toShort()
                    addrs.add(FtnAddr(zone, net, node, 0))
                }
            }
        }
    }

    private fun scanPointDirectory(
        dir: Path,
        zone: Short,
        net: Short,
        node: Short,
        addrs: MutableSet<FtnAddr>
    ) {
        SystemFileSystem.list(dir).forEach { path ->
            val name = path.name
            if (name.length >= 8 && name.substring(0, 8)
                    .all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            ) {
                val point = name.substring(0, 8).toLong(16).toShort()
                addrs.add(FtnAddr(zone, net, node, point))
            }
        }
    }

    public fun getLink(addr: FtnAddr): BsoLink {
        val dir = getDirectoryForAddr(addr)
        val baseName = getBaseFileName(addr)

        var netmail: BsoNetmail? = null
        val references = mutableListOf<BsoReference>()
        var hasRequest = false
        var isBusy = false
        var isCalling = false
        var isHold = false

        if (SystemFileSystem.exists(dir)) {
            SystemFileSystem.list(dir).forEach { path ->
                val name = path.name
                if (name.startsWith(
                        baseName,
                        ignoreCase = true
                    ) && name.length == baseName.length + 4
                ) {
                    val ext = name.substring(baseName.length + 1).lowercase()
                    when {
                        ext.endsWith("ut") -> {
                            val flavorCode = ext[0]
                            val flavor = FtnFlavor.entries.find { it.netmailCode == flavorCode }
                            if (flavor != null) {
                                netmail = BsoNetmail(path, flavor)
                            }
                        }

                        ext.endsWith("lo") -> {
                            val flavorCode = ext[0]
                            val flavor = FtnFlavor.entries.find { it.flowCode == flavorCode }
                            if (flavor != null) {
                                references.addAll(readFlowFile(path, flavor))
                            }
                        }

                        ext == "req" -> hasRequest = true
                        ext == "bsy" -> isBusy = true
                        ext == "csy" -> isCalling = true
                        ext == "hld" -> isHold = true
                    }
                }
            }
        }

        return BsoLink(addr, netmail, references, hasRequest, isBusy, isCalling, isHold)
    }

    private fun readFlowFile(path: Path, flavor: FtnFlavor): List<BsoReference> {
        val refs = mutableListOf<BsoReference>()
        SystemFileSystem.source(path).buffered().use { source ->
            while (!source.exhausted()) {
                val line = source.readLine()?.trim() ?: break
                if (line.isEmpty()) continue

                val firstChar = line[0]
                val directive = BsoDirective.fromCode(firstChar)
                val filePathStr = if (directive != BsoDirective.NONE) {
                    line.substring(1).trim()
                } else {
                    line
                }
                if (filePathStr.isNotEmpty()) {
                    refs.add(BsoReference(Path(filePathStr), flavor, directive))
                }
            }
        }
        return refs
    }

    private fun isAbsolutePath(path: Path): Boolean {
        val s = path.toString()
        if (s.isEmpty()) return false
        if (s.startsWith("/")) return true
        if (s.startsWith("\\")) return true
        if (s.length >= 2 && s[1] == ':' && s[0].isLetter()) return true
        return false
    }

    public suspend fun addReference(addr: FtnAddr, reference: BsoReference) {
        val lock = getLock(addr)
        lock.withLock {
            val dir = getDirectoryForAddr(addr)
            if (!SystemFileSystem.exists(dir)) {
                SystemFileSystem.createDirectories(dir)
            }

            // Check for .bsy file
            val baseName = getBaseFileName(addr)
            val bsyPath = Path(dir, "$baseName.bsy")
            if (SystemFileSystem.exists(bsyPath)) {
                throw IllegalStateException("Link $addr is busy (BSY file exists)")
            }

            val flavor = reference.flavor
            val ext = "${flavor.flowCode}lo"
            val flowPath = Path(dir, "$baseName.$ext")

            val directiveChar = reference.directive.code?.toString() ?: ""
            val resolvedPath = if (isAbsolutePath(reference.path)) {
                reference.path
            } else {
                Path(rootPath, reference.path.toString())
            }
            val line = directiveChar + resolvedPath.toString() + "\n"

            SystemFileSystem.sink(flowPath, append = true).buffered().use { sink ->
                sink.writeString(line)
            }
        }
    }
    public suspend fun setBusy(addr: FtnAddr, info: String? = null) {
        val lock = getLock(addr)
        lock.withLock {
            val dir = getDirectoryForAddr(addr)
            if (!SystemFileSystem.exists(dir)) SystemFileSystem.createDirectories(dir)
            val path = Path(dir, "${getBaseFileName(addr)}.bsy")
            if (SystemFileSystem.exists(path)) throw IllegalStateException("Link $addr is already busy")
            SystemFileSystem.sink(path).buffered().use { 
                if (info != null) it.writeString(info.take(70)) 
            }
        }
    }

    public suspend fun unsetBusy(addr: FtnAddr) {
        val lock = getLock(addr)
        lock.withLock {
            val path = Path(getDirectoryForAddr(addr), "${getBaseFileName(addr)}.bsy")
            if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
        }
    }

    public suspend fun setCalling(addr: FtnAddr, info: String? = null) {
        val lock = getLock(addr)
        lock.withLock {
            val dir = getDirectoryForAddr(addr)
            if (!SystemFileSystem.exists(dir)) SystemFileSystem.createDirectories(dir)
            val path = Path(dir, "${getBaseFileName(addr)}.csy")
            if (SystemFileSystem.exists(path)) throw IllegalStateException("Link $addr is already calling")
            SystemFileSystem.sink(path).buffered().use { 
                if (info != null) it.writeString(info.take(70)) 
            }
        }
    }

    public suspend fun unsetCalling(addr: FtnAddr) {
        val lock = getLock(addr)
        lock.withLock {
            val path = Path(getDirectoryForAddr(addr), "${getBaseFileName(addr)}.csy")
            if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
        }
    }

    public suspend fun setHold(addr: FtnAddr, untilTimestamp: Long) {
        val lock = getLock(addr)
        lock.withLock {
            val dir = getDirectoryForAddr(addr)
            if (!SystemFileSystem.exists(dir)) SystemFileSystem.createDirectories(dir)
            val path = Path(dir, "${getBaseFileName(addr)}.hld")
            SystemFileSystem.sink(path).buffered().use { 
                it.writeString(untilTimestamp.toString()) 
            }
        }
    }

    public suspend fun unsetHold(addr: FtnAddr) {
        val lock = getLock(addr)
        lock.withLock {
            val path = Path(getDirectoryForAddr(addr), "${getBaseFileName(addr)}.hld")
            if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
        }
    }

    public suspend fun deleteFlowFiles(addr: FtnAddr) {
        val lock = getLock(addr)
        lock.withLock {
            val dir = getDirectoryForAddr(addr)
            if (!SystemFileSystem.exists(dir)) return

            val baseName = getBaseFileName(addr)
            SystemFileSystem.list(dir).forEach { path ->
                val name = path.name
                if (name.startsWith(
                        baseName,
                        ignoreCase = true
                    ) && name.length == baseName.length + 4
                ) {
                    val ext = name.substring(baseName.length + 1).lowercase()
                    if (ext.endsWith("ut") || ext.endsWith("lo") || ext == "req") {
                        SystemFileSystem.delete(path)
                    }
                }
            }
        }
    }
}
