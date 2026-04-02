package com.quizduel.app.ui.profile

object AvatarUtils {

    // Each avatar has an id, a display name, and a DiceBear seed
    data class Avatar(val id: Int, val name: String, val seed: String)

    private val avatars = listOf(
        // Bottts — Robots
        Avatar(1, "Bolt", "bolt-x1"),
        Avatar(2, "Nova", "nova-z9"),
        Avatar(3, "Glitch", "glitch-7"),
        Avatar(4, "Pixel", "pixel-q4"),
        Avatar(5, "Zap", "zap-v2"),

        // Lorelei — Characters
        Avatar(6, "Luna", "luna-l1"),
        Avatar(7, "Aria", "aria-s3"),
        Avatar(8, "Zara", "zara-f5"),
        Avatar(9, "Mira", "mira-k7"),
        Avatar(10, "Nova", "nova-p9"),

        // Adventurer — Fantasy
        Avatar(11, "Blaze", "blaze-a1"),
        Avatar(12, "Cypher", "cypher-b3"),
        Avatar(13, "Vortex", "vortex-c5"),
        Avatar(14, "Nexus", "nexus-d7"),
        Avatar(15, "Titan", "titan-e9")
    )

    fun getAllAvatars() = avatars

    fun getAllAvatarIds() = avatars.map { it.id }

    fun getAvatarUrl(avatarId: Int): String {
        val avatar = avatars.find { it.id == avatarId } ?: avatars[0]
        val style = when (avatarId) {
            in 1..5 -> "bottts"
            in 6..10 -> "lorelei"
            in 11..15 -> "adventurer"
            else -> "bottts"
        }
        return "https://api.dicebear.com/7.x/$style/svg?seed=${avatar.seed}&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf"
    }

    fun getAvatarName(avatarId: Int): String {
        return avatars.find { it.id == avatarId }?.name ?: "Warrior"
    }
}