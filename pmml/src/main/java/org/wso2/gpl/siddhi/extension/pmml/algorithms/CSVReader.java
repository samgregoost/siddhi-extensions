package org.wso2.gpl.siddhi.extension.pmml.algorithms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sameera on 11/23/16.
 */
public class CSVReader {
    private BufferedReader br;
    private boolean hasNext;
    private final char separator;
    private final char quotechar;
    private final char escape;
    private int skipLines;
    private boolean linesSkiped;
    public static final char DEFAULT_SEPARATOR = ',';
    public static final int INITIAL_READ_SIZE = 64;
    public static final char DEFAULT_QUOTE_CHARACTER = '\"';
    public static final char DEFAULT_ESCAPE_CHARACTER = '\\';
    public static final int DEFAULT_SKIP_LINES = 0;

    public CSVReader(Reader reader) {
        this(reader, ',');
    }

    public CSVReader(Reader reader, char separator) {
        this(reader, separator, '\"', '\\');
    }

    public CSVReader(Reader reader, char separator, char quotechar) {
        this(reader, separator, quotechar, '\\', 0);
    }

    public CSVReader(Reader reader, char separator, char quotechar, char escape) {
        this(reader, separator, quotechar, escape, 0);
    }

    public CSVReader(Reader reader, char separator, char quotechar, int line) {
        this(reader, separator, quotechar, '\\', line);
    }

    public CSVReader(Reader reader, char separator, char quotechar, char escape, int line) {
        this.hasNext = true;
        this.br = new BufferedReader(reader);
        this.separator = separator;
        this.quotechar = quotechar;
        this.escape = escape;
        this.skipLines = line;
    }

    public List<String[]> readAll() throws IOException {
        ArrayList allElements = new ArrayList();

        while(this.hasNext) {
            String[] nextLineAsTokens = this.readNext();
            if(nextLineAsTokens != null) {
                allElements.add(nextLineAsTokens);
            }
        }

        return allElements;
    }

    public String[] readNext() throws IOException {
        String nextLine = this.getNextLine();
        return this.hasNext?this.parseLine(nextLine):null;
    }

    private String getNextLine() throws IOException {
        if(!this.linesSkiped) {
            for(int nextLine = 0; nextLine < this.skipLines; ++nextLine) {
                this.br.readLine();
            }

            this.linesSkiped = true;
        }

        String var2 = this.br.readLine();
        if(var2 == null) {
            this.hasNext = false;
        }

        return this.hasNext?var2:null;
    }

    private String[] parseLine(String nextLine) throws IOException {
        if(nextLine == null) {
            return null;
        } else {
            ArrayList tokensOnThisLine = new ArrayList();
            StringBuilder sb = new StringBuilder(64);
            boolean inQuotes = false;

            do {
                if(inQuotes) {
                    sb.append("\n");
                    nextLine = this.getNextLine();
                    if(nextLine == null) {
                        break;
                    }
                }

                for(int i = 0; i < nextLine.length(); ++i) {
                    char c = nextLine.charAt(i);
                    if(c == this.escape) {
                        if(this.isEscapable(nextLine, inQuotes, i)) {
                            sb.append(nextLine.charAt(i + 1));
                            ++i;
                        } else {
                            ++i;
                        }
                    } else if(c == this.quotechar) {
                        if(this.isEscapedQuote(nextLine, inQuotes, i)) {
                            sb.append(nextLine.charAt(i + 1));
                            ++i;
                        } else {
                            inQuotes = !inQuotes;
                            if(i > 2 && nextLine.charAt(i - 1) != this.separator && nextLine.length() > i + 1 && nextLine.charAt(i + 1) != this.separator) {
                                sb.append(c);
                            }
                        }
                    } else if(c == this.separator && !inQuotes) {
                        tokensOnThisLine.add(sb.toString());
                        sb = new StringBuilder(64);
                    } else {
                        sb.append(c);
                    }
                }
            } while(inQuotes);

            tokensOnThisLine.add(sb.toString());
            return (String[])tokensOnThisLine.toArray(new String[0]);
        }
    }

    private boolean isEscapedQuote(String nextLine, boolean inQuotes, int i) {
        return inQuotes && nextLine.length() > i + 1 && nextLine.charAt(i + 1) == this.quotechar;
    }

    private boolean isEscapable(String nextLine, boolean inQuotes, int i) {
        return inQuotes && nextLine.length() > i + 1 && (nextLine.charAt(i + 1) == this.quotechar || nextLine.charAt(i + 1) == this.escape);
    }

    public void close() throws IOException {
        this.br.close();
    }
}
