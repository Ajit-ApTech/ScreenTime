package com.screentime.admin.models

data class ChildChipItem(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val lastSeen: Long,
    val familyId: String? = null
)

data class FamilyItem(
    val familyId: String,
    val familyName: String,
    val inviteCode: String,
    val children: List<ChildChipItem> = emptyList()
)
