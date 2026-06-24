import java.security.MessageDigest

/**
 * Standalone license key generator for GDC Library app.
 * Run: kotlinc keygen.kt -include-runtime -d keygen.jar && java -jar keygen.jar <DEVICE_ID>
 *
 * The device ID is shown on the license screen (without dashes).
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: java -jar keygen.jar <DEVICE_ID>")
        println("  DEVICE_ID: the 32-char hex ID shown on the app's license screen (without dashes)")
        println()
        println("Example: java -jar keygen.jar A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4")
        return
    }

    val deviceId = args[0].uppercase().replace("-", "")
    if (deviceId.length != 32) {
        println("ERROR: Device ID must be 32 hex characters. Got ${deviceId.length}.")
        return
    }

    val secret = "GDC_LIBRARY_2024_SECRET"
    val input = "$deviceId:$secret"
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    val hash = digest.joinToString("") { "%02X".format(it) }.take(12)
    val key = "GDCLIB-${hash.chunked(4).joinToString("-")}"

    println("┌─────────────────────────────────────┐")
    println("│  GDC Library — License Key Generator │")
    println("├─────────────────────────────────────┤")
    println("│  Device ID: $deviceId  │")
    println("│  License:   $key        │")
    println("└─────────────────────────────────────┘")
}
