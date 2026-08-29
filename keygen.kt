import java.security.MessageDigest

/**
 * GDC Library — Standalone License Key Generator (CLI)
 *
 * Build:  kotlinc keygen.kt -include-runtime -d keygen.jar
 * Run:    java -jar keygen.jar <DEVICE_ID> [DEVICE_ID2] ...
 *
 * DEVICE_ID: the 32-char hex Machine ID shown on the app's License screen.
 *            Dashes are optional and will be stripped automatically.
 *
 * Examples:
 *   java -jar keygen.jar A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4
 *   java -jar keygen.jar A1B2-C3D4-E5F6-A1B2-C3D4-E5F6-A1B2-C3D4
 */

private const val SECRET = "GDC_LIBRARY_2024_SECRET"
private const val PREFIX = "GDCLIB"

fun generateKeyHash(deviceId: String): String {
    val input = "$deviceId:$SECRET"
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return digest.joinToString("") { "%02X".format(it) }.take(12)
}

fun generateLicenseKey(deviceId: String): String {
    val hash = generateKeyHash(deviceId)
    val formatted = hash.chunked(4).joinToString("-")
    return "$PREFIX-$formatted"
}

fun printBanner() {
    println("╔══════════════════════════════════════════════════╗")
    println("║     GDC Library — License Key Generator v2.0    ║")
    println("║          ⚠  ADMIN TOOL — KEEP PRIVATE ⚠          ║")
    println("╚══════════════════════════════════════════════════╝")
    println()
}

fun printHelp() {
    printBanner()
    println("Usage:")
    println("  java -jar keygen.jar <DEVICE_ID> [DEVICE_ID2] ...")
    println()
    println("Arguments:")
    println("  DEVICE_ID   32-char hex Machine ID from the app's License screen")
    println("              (dashes optional, e.g. A1B2C3D4... or A1B2-C3D4-...)")
    println()
    println("Examples:")
    println("  java -jar keygen.jar A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4")
    println("  java -jar keygen.jar A1B2-C3D4-E5F6-A1B2 C3D4E5F6A1B2C3D4")
    println()
}

fun processDeviceId(raw: String): Boolean {
    val deviceId = raw.uppercase().replace("-", "").trim()

    if (!Regex("^[A-F0-9]+$").matches(deviceId)) {
        System.err.println("  ERROR: \"$raw\" contains invalid characters. Only hex (A-F, 0-9) allowed.")
        return false
    }
    if (deviceId.length != 32) {
        System.err.println("  ERROR: \"$raw\" has ${deviceId.length} chars — must be exactly 32 hex characters.")
        return false
    }

    val key = generateLicenseKey(deviceId)
    val fmtId = deviceId.chunked(4).joinToString("-")

    println("  ┌──────────────────────────────────────────────┐")
    println("  │  Machine ID : $fmtId  │")
    println("  │  License Key: $key             │")
    println("  └──────────────────────────────────────────────┘")
    println("  Validity: 365 days from device activation date")
    println()
    return true
}

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help", "help")) {
        printHelp()
        return
    }

    printBanner()

    var success = 0
    var failed = 0

    println("Generating ${args.size} license key(s)...\n")

    for (arg in args) {
        if (processDeviceId(arg)) success++ else failed++
    }

    println("─".repeat(52))
    if (failed == 0) {
        println("✅  Done. Generated $success key(s) successfully.")
    } else {
        println("⚠️  Done. $success succeeded, $failed failed.")
    }
}
