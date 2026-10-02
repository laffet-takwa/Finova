package com.finova.common.demo;

/**
 * Fixed identifiers for the seeded demo identities.
 *
 * <p>The demo seeders of {@code user-service}, {@code account-service} and
 * {@code transaction-service} all need to refer to the same demo customers, but
 * each service generates its own random UUIDs for real users. Resolving the ids
 * across services at startup would need a privileged lookup, and a service token
 * is a {@code CUSTOMER} to every service in this platform — so there is no safe
 * way to ask "what is takwa@finova.dev's id?" from another service.
 *
 * <p>Giving the demo identities <em>fixed</em> ids removes that dependency
 * entirely: every seeder references the same constants, the data lines up, and
 * nothing has to be reachable for the platform to start with usable demo data.
 * Only the {@code dev} profile uses these ids; real registered users get random
 * UUIDs like any other.
 */
public final class DemoIdentities {

    public static final String ADMIN_ID = "00000000-0000-4000-8000-000000000001";
    public static final String TAKWA_ID = "00000000-0000-4000-8000-000000000002";
    public static final String INES_ID = "00000000-0000-4000-8000-000000000003";
    public static final String YASSINE_ID = "00000000-0000-4000-8000-000000000004";
    public static final String SALMA_ID = "00000000-0000-4000-8000-000000000005";
    public static final String SAMI_ID = "00000000-0000-4000-8000-000000000006";

    public static final String ADMIN_EMAIL = "admin@finova.dev";
    public static final String TAKWA_EMAIL = "takwa@finova.dev";
    public static final String INES_EMAIL = "ines.bouzid@finova.dev";
    public static final String YASSINE_EMAIL = "yassine.trabelsi@finova.dev";
    public static final String SALMA_EMAIL = "salma.gharbi@finova.dev";
    public static final String SAMI_EMAIL = "sami.mejboud@finova.dev";

    public static final String DEMO_PASSWORD = "Finova#2026";

    /** Opening balances, shared so the dashboard and the ledger agree exactly. */
    public static final String TAKWA_CHECKING_NUMBER = "TN58 1000 0123 4567 8901 23";
    public static final String TAKWA_SAVINGS_NUMBER = "TN58 1000 0123 4567 8901 34";
    public static final String INES_CHECKING_NUMBER = "TN58 2000 0345 6677 8811 05";
    public static final String YASSINE_CHECKING_NUMBER = "TN58 2000 0789 1234 5678 90";
    public static final String YASSINE_EUR_NUMBER = "EU76 3000 1122 3344 5566 77";
    public static final String SALMA_SAVINGS_NUMBER = "TN58 3000 0567 8899 0011 22";
    public static final String SAMI_CHECKING_NUMBER = "TN58 3000 0900 1122 3344 55";

    private DemoIdentities() {
    }
}