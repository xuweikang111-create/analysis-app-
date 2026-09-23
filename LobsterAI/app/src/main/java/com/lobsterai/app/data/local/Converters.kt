package com.lobsterai.app.data.local

import androidx.room.TypeConverter
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.KnowledgeType
import com.lobsterai.app.domain.model.ProviderType

class Converters {
    @TypeConverter
    fun chatRoleToString(value: ChatRole): String = value.name

    @TypeConverter
    fun stringToChatRole(value: String): ChatRole = ChatRole.valueOf(value)

    @TypeConverter
    fun knowledgeTypeToString(value: KnowledgeType): String = value.name

    @TypeConverter
    fun stringToKnowledgeType(value: String): KnowledgeType = KnowledgeType.valueOf(value)

    @TypeConverter
    fun providerTypeToString(value: ProviderType): String = value.name

    @TypeConverter
    fun stringToProviderType(value: String): ProviderType = ProviderType.valueOf(value)
}