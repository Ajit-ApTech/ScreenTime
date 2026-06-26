package com.screentime.kids.models

data class ChildChipItem(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val lastSeen: Long
)
