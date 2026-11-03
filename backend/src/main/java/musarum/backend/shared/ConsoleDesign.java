package musarum.backend.shared;

import org.springframework.boot.Banner;
import org.springframework.core.env.Environment;
import java.io.PrintStream;

public final class ConsoleDesign implements Banner {

    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String[] GRADIENT = {"\u001B[38;5;57m", "\u001B[38;5;63m", "\u001B[38;5;69m", "\u001B[38;5;75m", "\u001B[38;5;81m", "\u001B[38;5;87m"};
    private static final String BORDER  = "\033[38;5;94m"; 
    private static final String TEAL    = "\033[38;5;37m";
    private static final String BEIGE   = "\033[38;5;230m";
    private static final String CIAN   = "\033[38;5;159m";
    private static final String AGUAMARINA   = "\033[38;5;195m";
    
    private static final String[] LOGO = {
        " ♫   ♪ ███╗ ♫ ███╗♯  ♫██╗  ♫██╗♪ ♯ ███████╗♫  ♪ █████╗ ♪  ♯██████╗ ♪  ♫██╗ ♪ ██╗♯ ♫ ███╗ ♫ ███╗ ♫   ♭ ♪",
        "  ♪ ♯ ♫████╗ ████║♪ ♯ ██║♪  ██║♯ ♫ ██╔════╝♪  ♯██╔══██╗  ♫ ██╔══██╗  ♯ ██║♫  ██║ ♪  ████╗ ████║ ♩ ♮   ♫",
        "♫  ♯ ♪ ██╔████╔██║  ♫ ██║  ♪██║ ♪  ███████╗ ♯  ███████║♯ ♪ ██████╔╝♫  ♪██║  ♪██║   ♪██╔████╔██║♯ ♯ ♪  ♫",
        "♯ ♪  ♫ ██║╚██╔╝██║ ♯  ██║♫  ██║♫  ♯╚════██║  ♪ ██╔══██║♫ ♯ ██╔══██╗♯   ██║ ♫ ██║♯  ♯██║╚██╔╝██║♪  ♮ ♯",
        " ♯ ♫   ██║♪╚═╝♫██║♪  ♪╚██████╔╝♯ ♪ ███████║♪ ♫ ██║♫ ██║  ♪ ██║  ██║  ♪ ╚██████╔╝ ♫  ██║♯╚═╝ ██║ ♭  ♫",
        "♪  ♯  ♪╚═╝ ♫  ♪╚═╝♫  ♯ ╚═════╝♪ ♯  ╚══════╝♪  ♯╚═╝ ♪╚═╝ ♫  ╚═╝♫ ╚═╝♫   ♯╚═════╝ ♪  ♯╚═╝ ♯ ♫ ╚═╝♯ ♭  ♪",
        BOLD + CIAN  + "                                    ✦  CARLOS SÁNCHEZ MIRANDA  ✦       " + RESET,
        BOLD + AGUAMARINA    + "                                             ✦  2026  ✦               " + RESET
    };

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        out.println();

        for (int i = 0; i < LOGO.length; i++) {
            String color = GRADIENT[Math.min(i, GRADIENT.length - 1)];
            out.println(BOLD + color + "  " + LOGO[i] + RESET);
        }
        
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath.isEmpty()) contextPath = "/api";
        int boxWidth = 64;
        out.println(BORDER + "                     ╔" + "═".repeat(boxWidth) + "╗" + RESET);
        printRow(out, "📡", "API BASE", "http://localhost:" + port + contextPath, boxWidth);
        printRow(out, "📖", "SWAGGER", "http://localhost:" + port + contextPath + "/swagger-ui/index.html", boxWidth);
        out.println(BORDER + "                     ╚" + "═".repeat(boxWidth) + "╝" + RESET);
    }

    private void printRow(PrintStream out, String icon, String label, String value, int boxWidth) {
        String paddedLabel = String.format("%-8s", label);
        int leftVisualSpace = 14; 
        int spacesNeeded = boxWidth - leftVisualSpace - value.length();
        String spaces = spacesNeeded > 0 ? " ".repeat(spacesNeeded) : "";
        out.println(BORDER + "                     ║ " + RESET + TEAL + BOLD + icon + "" + paddedLabel + ": " + RESET + BEIGE + BOLD + value + RESET + spaces + BORDER + " ║" + RESET);
    }
}