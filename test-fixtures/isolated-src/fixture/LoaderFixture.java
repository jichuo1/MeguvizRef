package fixture;
public final class LoaderFixture {
    static { System.setProperty("meguviz.fixture.initialized", "yes"); }
    public static String value() { return "fixture"; }
    public LoaderFixture echo(LoaderFixture other) { return other; }
}
