package com.college.library.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object SafeStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SafeString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        return runCatching { decoder.decodeString() }.getOrDefault("")
    }
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class Member(
    val syncId: String = "",
    val id: Long = 0,
    val memberId: String = "",
    val name: String = "Unknown Member",
    @Serializable(with = SafeStringSerializer::class) val email: String = "",
    @Serializable(with = SafeStringSerializer::class) val phone: String = "",
    @Serializable(with = SafeStringSerializer::class) val department: String = "",
    @Serializable(with = SafeStringSerializer::class) val memberType: String = "Student",
    @Serializable(with = SafeStringSerializer::class) val joinDate: String = "",
    @Serializable(with = SafeStringSerializer::class) val expiryDate: String = "",
    val booksIssued: Int = 0,
    @Serializable(with = SafeStringSerializer::class) val fatherName: String = "",
    @Serializable(with = SafeStringSerializer::class) val className: String = "",
    @Serializable(with = SafeStringSerializer::class) val classNo: String = "",
    @Serializable(with = SafeStringSerializer::class) val address: String = "",
    val photoUri: String? = null,
    @Serializable(with = SafeStringSerializer::class) val designation: String = "",
    @Serializable(with = SafeStringSerializer::class) val bps: String = "",
    @Serializable(with = SafeStringSerializer::class) val pin: String = "",
    @Serializable(with = SafeStringSerializer::class) val biometricHash: String = "",
    @Serializable(with = SafeStringSerializer::class) val biometricEnrolDate: String = "",
    @Serializable(with = SafeStringSerializer::class) val biometricLastVerified: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    @Serializable(with = SafeStringSerializer::class) val collegeId: String = "",
    @Serializable(with = SafeStringSerializer::class) val syncStatus: String = "synced"
)


