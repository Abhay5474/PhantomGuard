package com.phantomguard.common.policy;

import java.util.Map;
import java.util.Set;

/**
 * Built-in seed mapping of registrable domains to content categories. The
 * control plane materializes this catalog into the Redis
 * {@code pg:domain:categories} hash on startup; operators can extend the hash
 * at runtime (e.g. from a threat-intel feed) without redeploying.
 */
public final class CategoryCatalog {

    private CategoryCatalog() {
    }

    private static final Map<ContentCategory, Set<String>> SEED = Map.of(
            ContentCategory.ADULT_CONTENT, Set.of(
                    "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com",
                    "youporn.com", "onlyfans.com", "chaturbate.com", "stripchat.com"),
            ContentCategory.SOCIAL_MEDIA, Set.of(
                    "tiktok.com", "tiktokcdn.com", "instagram.com", "cdninstagram.com",
                    "facebook.com", "fbcdn.net", "snapchat.com", "sc-cdn.net",
                    "twitter.com", "x.com", "reddit.com", "redd.it",
                    "discord.com", "discord.gg", "discordapp.com"),
            ContentCategory.GAMING, Set.of(
                    "roblox.com", "rbxcdn.com", "epicgames.com", "fortnite.com",
                    "minecraft.net", "steampowered.com", "steamcommunity.com",
                    "ea.com", "riotgames.com", "leagueoflegends.com", "twitch.tv"),
            ContentCategory.GAMBLING, Set.of(
                    "bet365.com", "draftkings.com", "fanduel.com", "pokerstars.com",
                    "stake.com", "betway.com", "888casino.com"),
            ContentCategory.STREAMING, Set.of(
                    "youtube.com", "ytimg.com", "googlevideo.com", "netflix.com",
                    "nflxvideo.net", "hulu.com", "disneyplus.com", "max.com"));

    public static Map<ContentCategory, Set<String>> seedDomains() {
        return SEED;
    }
}
