import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

public class LibraryBookTracker {

    // ─── Output format ────────────────────────────────────────────────────────
    static final String HDR_FMT   = "%-30s %-20s %-15s %5s%n";
    static final String ROW_FMT   = "%-30s %-20s %-15s %5d%n";
    static final String SEPARATOR  = "-".repeat(73);

    // ─── State ────────────────────────────────────────────────────────────────
    static File       catalogFile;
    static File       logFile;
    static List<Book> books      = new ArrayList<>();
    static int        errorCount = 0;

    // ═══════════════════════════════════════════════════════════════════
    // Entry point
    // ═══════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        try {
            run(args);
        } catch (InsufficientArgumentsException | InvalidFileNameException e) {
            System.err.println("Error: " + e.getMessage());
            logError("<startup>", e);
        } catch (DuplicateISBNException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        } finally {
            System.out.println("Thank you for using the Library Book Tracker.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Core logic
    // ═══════════════════════════════════════════════════════════════════

    static void run(String[] args) throws Exception {

        // 1. Argument count check
        if (args.length < 2)
            throw new InsufficientArgumentsException(
                "Usage: java LibraryBookTracker <catalogFile.txt> <operationArgument>");

        // 2. File name validation
        String filePath = args[0];
        if (!filePath.toLowerCase().endsWith(".txt"))
            throw new InvalidFileNameException(
                "Catalog file must end with .txt, got: " + filePath);

        catalogFile = new File(filePath);

        File parentDir = catalogFile.getParentFile();
        if (parentDir == null) parentDir = new File(".");

        logFile = new File(parentDir, "errors.log");

        // Auto-create missing directories and/or file
        if (!parentDir.exists())
            parentDir.mkdirs();
        if (!catalogFile.exists())
            catalogFile.createNewFile();

        // 3. Read and validate catalog
        int validRecords = readCatalog();

        // 4. Determine and execute operation
        String op           = args[1];
        int    resultsCount = 0;
        int    addedCount   = 0;

        if (op.matches("\\d{13}")) {
            resultsCount = searchByISBN(op);
        } else if (op.split(":").length == 4) {
            addedCount = addBook(op);
        } else {
            resultsCount = searchByTitle(op);
        }

        // 5. Statistics
        System.out.println();
        System.out.println("=== Statistics ===");
        System.out.printf("Valid records processed : %d%n", validRecords);
        System.out.printf("Search results          : %d%n", resultsCount);
        System.out.printf("Books added             : %d%n", addedCount);
        System.out.printf("Errors encountered      : %d%n", errorCount);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Read catalog
    // ═══════════════════════════════════════════════════════════════════

    static int readCatalog() throws IOException {
        int valid = 0;
        List<String> lines = Files.readAllLines(catalogFile.toPath());
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                books.add(Book.parse(line));
                valid++;
            } catch (BookCatalogException e) {
                errorCount++;
                logError(line, e);
            }
        }
        return valid;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Operations
    // ═══════════════════════════════════════════════════════════════════

    static int searchByTitle(String keyword) {
        System.out.println("\n=== Title Search: \"" + keyword + "\" ===");
        printHeader();
        List<Book> results = books.stream()
            .filter(b -> b.title != null && b.title.toLowerCase().contains(keyword.toLowerCase()))
            .collect(Collectors.toList());
        if (results.isEmpty()) {
            System.out.println("No results found.");
        } else {
            results.forEach(LibraryBookTracker::printBook);
        }
        return results.size();
    }

    static int searchByISBN(String isbn) throws DuplicateISBNException {
        System.out.println("\n=== ISBN Search: " + isbn + " ===");
        List<Book> results = books.stream()
            .filter(b -> b.isbn.equals(isbn))
            .collect(Collectors.toList());
        if (results.size() > 1) {
            DuplicateISBNException ex = new DuplicateISBNException(
                "Multiple books found with ISBN " + isbn);
            logError(isbn, ex);
            errorCount++;
            throw ex;
        }
        printHeader();
        if (results.isEmpty()) {
            System.out.println("No results found.");
            return 0;
        }
        printBook(results.get(0));
        return 1;
    }

    static int addBook(String record) throws IOException {
        try {
            Book newBook = Book.parse(record);
            boolean dup = books.stream().anyMatch(b -> b.isbn.equals(newBook.isbn));
            if (dup) {
                DuplicateISBNException ex = new DuplicateISBNException(
                    "ISBN " + newBook.isbn + " already exists in catalog");
                logError(record, ex);
                errorCount++;
                System.err.println("Error: " + ex.getMessage());
                return 0;
            }
            books.add(newBook);
            books.sort(Comparator.comparing(b -> b.title.toLowerCase()));
            saveBooks();
            System.out.println("\n=== Book Added ===");
            printHeader();
            printBook(newBook);
            return 1;
        } catch (BookCatalogException e) {
            errorCount++;
            logError(record, e);
            System.err.println("Error: " + e.getMessage());
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    static void printHeader() {
        System.out.printf(HDR_FMT, "Title", "Author", "ISBN", "Copies");
        System.out.println(SEPARATOR);
    }

    static void printBook(Book b) {
        System.out.printf(ROW_FMT, b.title, b.author, b.isbn, b.copies);
    }

    static void saveBooks() throws IOException {
        List<String> lines = books.stream()
            .map(Book::toLine)
            .collect(Collectors.toList());
        Files.write(catalogFile.toPath(), lines);
    }

    static void logError(String offendingText, Exception e) {
        try {
            // ✅ Fix: if logFile is null (error happened before run() set it),
            //         fall back to errors.log in the current working directory.
            File target = (logFile != null) ? logFile : new File(".", "errors.log");

            String entry = String.format(
                "[%s] INVALID: \"%s\" - %s: %s%n",
                LocalDateTime.now(),
                offendingText,
                e.getClass().getSimpleName(),
                e.getMessage()
            );
            Files.write(
                target.toPath(),
                entry.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ioe) {
            System.err.println("Failed to write to errors.log: " + ioe.getMessage());
        }
    }
}
