public class Book {

    public String title;
    public String author;
    public String isbn;
    public int    copies;

    public Book(String title, String author, String isbn, int copies) {
        this.title  = title;
        this.author = author;
        this.isbn   = isbn;
        this.copies = copies;
    }

    /**
     * Parses and validates a "Title:Author:ISBN:Copies" line.
     * Throws a BookCatalogException (or subclass) if anything is invalid.
     */
    public static Book parse(String line) throws BookCatalogException {

        String[] parts = line.split(":");

        if (parts.length != 4)
            throw new MalformedBookEntryException(
                "Expected 4 fields separated by ':' but got " + parts.length);

        String title     = parts[0].trim();
        String author    = parts[1].trim();
        String isbn      = parts[2].trim();
        String copiesStr = parts[3].trim();

        if (title.isEmpty())
            throw new MalformedBookEntryException("Title must not be empty");

        if (author.isEmpty())
            throw new MalformedBookEntryException("Author must not be empty");

        if (!isbn.matches("\\d{13}"))
            throw new InvalidISBNException(
                "ISBN must be exactly 13 digits, got: '" + isbn + "'");

        int copies;
        try {
            copies = Integer.parseInt(copiesStr);
        } catch (NumberFormatException e) {
            throw new MalformedBookEntryException(
                "Copies must be an integer, got: '" + copiesStr + "'");
        }

        if (copies <= 0)
            throw new MalformedBookEntryException(
                "Copies must be > 0, got: " + copies);

        return new Book(title, author, isbn, copies);
    }

    /** Serializes the book back to catalog file format. */
    public String toLine() {
        return title + ":" + author + ":" + isbn + ":" + copies;
    }

    @Override
    public String toString() {
        return toLine();
    }
}
