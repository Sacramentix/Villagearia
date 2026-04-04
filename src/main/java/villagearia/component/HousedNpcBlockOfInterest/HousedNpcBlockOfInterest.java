package villagearia.component.HousedNpcBlockOfInterest;

import java.util.regex.Pattern;

public enum HousedNpcBlockOfInterest {
    CHEST("^Furniture_.*Chest.*"),
    BENCH_TANNERY("^Bench_Tannery$"),
    BENCH_FURNACE("^Bench_Furnace$"),
    COOP_CHICKEN("^Coop_Chicken$");

    public final String query;
    public final Pattern pattern;

    HousedNpcBlockOfInterest(String query) {
        this.query = query;
        this.pattern = Pattern.compile(query);
    }
}