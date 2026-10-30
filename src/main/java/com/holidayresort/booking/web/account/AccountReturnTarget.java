package com.holidayresort.booking.web.account;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/** Local page allowlist, not a general redirect URL supplied by the browser. */
public final class AccountReturnTarget {
    public static final String HOME = "/preview/account";
    private AccountReturnTarget() { }

    public static String checked(String value) {
        if (value == null || value.isBlank()) return HOME;
        if (value.length() > 2048 || value.chars().anyMatch(c -> c < 32 || c == 127)
                || value.contains("\\") || value.contains("%")) throw invalid();
        final URI uri;
        try { uri = URI.create(value); } catch (IllegalArgumentException exception) { throw invalid(); }
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null) throw invalid();
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        if (Set.of(HOME, HOME + "/membership", "/preview/stays").contains(path) && query == null) return value;
        if ((HOME + "/membership/join").equals(path)
                && (query == null || query.equals("intent=join") || query.equals("intent=renew"))) return value;
        if ("/preview/stays/new".equals(path)) {
            if (query == null) return value;
            Set<String> fields = new HashSet<>();
            for (String pair : query.split("&", -1)) {
                String[] parts = pair.split("=", -1);
                if (parts.length != 2 || !Set.of("roomType", "arrival", "departure", "rooms").contains(parts[0])
                        || !fields.add(parts[0]) || !parts[1].matches("[A-Za-z0-9_-]+")) throw invalid();
            }
            return value;
        }
        throw invalid();
    }

    public static String label(String checked) {
        if (checked.startsWith("/preview/stays/new")) return "入住确认";
        if (checked.equals("/preview/stays")) return "我的预订";
        if (checked.startsWith(HOME + "/membership/join")) return checked.endsWith("intent=renew") ? "会员续费页面" : "会员开通页面";
        if (checked.equals(HOME + "/membership")) return "会员页面";
        return "个人中心";
    }

    private static AccountUiException invalid() {
        return new AccountUiException(400, "返回位置无效，请从页面入口重新开始。");
    }
}
