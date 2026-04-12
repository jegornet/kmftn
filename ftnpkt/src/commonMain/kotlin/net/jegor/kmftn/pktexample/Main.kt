package net.jegor.kmftn.pktexample

import kotlinx.io.files.Path
import net.jegor.kmftn.ftnpkt.*

internal fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: pktdump <file.pkt>")
        return
    }

    val pkt = PktReader.read(Path(args[0]))

    when (pkt) {
        is Pkt2plus -> {
            println("Packet type: Type-2+ (FSC-0048)")
            printCommonFields(pkt)
            println("  qOrigZone:    ${pkt.qOrigZone}")
            println("  qDestZone:    ${pkt.qDestZone}")
            println("  auxNet:       ${pkt.auxNet}")
            println("  capValid:     0x${pkt.capValid.toString(16)}")
            println("  prodCodeHi:   ${pkt.prodCodeHi}")
            println("  prodRevMinor: ${pkt.prodRevMinor}")
            println("  capWord:      0x${pkt.capWord.toString(16)}")
            println("  origPoint:    ${pkt.origPoint}")
            println("  destPoint:    ${pkt.destPoint}")
            println("  prodData:     ${pkt.prodData.hex()}")
        }
        is Pkt2e -> {
            println("Packet type: Type-2e (FSC-0039)")
            printCommonFields(pkt)
            println("  qOrigZone:    ${pkt.qOrigZone}")
            println("  qDestZone:    ${pkt.qDestZone}")
            println("  filler:       0x${pkt.filler.toString(16)}")
            println("  capValid:     0x${pkt.capValid.toString(16)}")
            println("  prodCodeHi:   ${pkt.prodCodeHi}")
            println("  prodRevMinor: ${pkt.prodRevMinor}")
            println("  capWord:      0x${pkt.capWord.toString(16)}")
            println("  origPoint:    ${pkt.origPoint}")
            println("  destPoint:    ${pkt.destPoint}")
            println("  prodData:     ${pkt.prodData.hex()}")
        }
        is Pkt2 -> {
            println("Packet type: Type-2 Stone Age (FTS-0001)")
            printCommonFields(pkt)
            println("  fill:         ${pkt.fill.hex()}")
        }
    }

    println()
    println("Messages: ${pkt.messages.size}")

    pkt.messages.forEachIndexed { i, msg ->
        println()
        println("--- Message #${i + 1} ---")
        println("  origNode:     ${msg.origNode}")
        println("  destNode:     ${msg.destNode}")
        println("  origNet:      ${msg.origNet}")
        println("  destNet:      ${msg.destNet}")
        println("  attribute:    0x${msg.attribute.toString(16)}${formatAttributes(msg)}")
        println("  cost:         ${msg.cost}")
        println("  dateTime:     ${msg.dateTime.hex()}")
        println("  toUserName:   ${msg.toUserName.hex()}")
        println("  fromUserName: ${msg.fromUserName.hex()}")
        println("  subject:      ${msg.subject.hex()}")
        println("  text:         ${msg.text.hex()}")
    }
}

private fun printCommonFields(pkt: Pkt) {
    println("  origNode:     ${pkt.origNode}")
    println("  destNode:     ${pkt.destNode}")
    println("  year:         ${pkt.year}")
    println("  month:        ${pkt.month}")
    println("  day:          ${pkt.day}")
    println("  hour:         ${pkt.hour}")
    println("  minute:       ${pkt.minute}")
    println("  second:       ${pkt.second}")
    println("  baud:         ${pkt.baud}")
    println("  origNet:      ${pkt.origNet}")
    println("  destNet:      ${pkt.destNet}")
    println("  origZone:     ${pkt.origZone}")
    println("  destZone:     ${pkt.destZone}")
    println("  prodCodeLo:   ${pkt.prodCodeLo}")
    println("  prodRevMajor: ${pkt.prodRevMajor}")
    println("  password:     ${pkt.password.hex()}")
}

private fun formatAttributes(msg: PackedMsg): String {
    val flags = buildList {
        if (msg.isPrivate) add("Private")
        if (msg.isCrash) add("Crash")
        if (msg.isRecd) add("Recd")
        if (msg.isSent) add("Sent")
        if (msg.isFileAttached) add("FileAttached")
        if (msg.isInTransit) add("InTransit")
        if (msg.isOrphan) add("Orphan")
        if (msg.isKillSent) add("KillSent")
        if (msg.isLocal) add("Local")
        if (msg.isHoldForPickup) add("HoldForPickup")
        if (msg.isFileRequest) add("FileRequest")
        if (msg.isReturnReceiptRequest) add("ReturnReceiptReq")
        if (msg.isReturnReceipt) add("ReturnReceipt")
        if (msg.isAuditRequest) add("AuditRequest")
        if (msg.isFileUpdateReq) add("FileUpdateReq")
    }
    return if (flags.isEmpty()) "" else " (${flags.joinToString(", ")})"
}

private fun ByteArray.hex(): String {
    if (isEmpty()) return "(empty)"
    return joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
