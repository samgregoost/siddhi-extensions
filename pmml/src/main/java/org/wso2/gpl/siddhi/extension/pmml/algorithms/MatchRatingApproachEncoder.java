package org.wso2.gpl.siddhi.extension.pmml.algorithms;

import java.util.Locale;

/**
 * Created by sameera on 11/23/16.
 */
public class MatchRatingApproachEncoder {

        private static final String SPACE = " ";
        private static final String EMPTY = "";
        private static final int ONE = 1;
        private static final int TWO = 2;
        private static final int THREE = 3;
        private static final int FOUR = 4;
        private static final int FIVE = 5;
        private static final int SIX = 6;
        private static final int SEVEN = 7;
        private static final int EIGHT = 8;
        private static final int ELEVEN = 11;
        private static final int TWELVE = 12;
        private static final String PLAIN_ASCII = "AaEeIiOoUuAaEeIiOoUuYyAaEeIiOoUuYyAaOoNnAaEeIiOoUuYyAaCcOoUu";
        private static final String UNICODE = "ÀàÈèÌìÒòÙùÁáÉéÍíÓóÚúÝýÂâÊêÎîÔôÛûŶŷÃãÕõÑñÄäËëÏïÖöÜüŸÿÅåÇçŐőŰű";
        private static final String[] DOUBLE_CONSONANT = new String[]{"BB", "CC", "DD", "FF", "GG", "HH", "JJ", "KK", "LL", "MM", "NN", "PP", "QQ", "RR", "SS", "TT", "VV", "WW", "XX", "YY", "ZZ"};

        public MatchRatingApproachEncoder() {
        }

        String cleanName(String name) {
            String upperName = name.toUpperCase(Locale.ENGLISH);
            String[] charsToTrim = new String[]{"\\-", "[&]", "\\\'", "\\.", "[\\,]"};
            String[] arr$ = charsToTrim;
            int len$ = charsToTrim.length;

            for(int i$ = 0; i$ < len$; ++i$) {
                String str = arr$[i$];
                upperName = upperName.replaceAll(str, "");
            }

            upperName = this.removeAccents(upperName);
            upperName = upperName.replaceAll("\\s+", "");
            return upperName;
        }

        public final Object encode(Object pObject){

                return this.encode((String)pObject);

        }

        public final String encode(String name) {
            if(name != null && !"".equalsIgnoreCase(name) && !" ".equalsIgnoreCase(name) && name.length() != 1) {
                name = this.cleanName(name);
                name = this.removeVowels(name);
                name = this.removeDoubleConsonants(name);
                name = this.getFirst3Last3(name);
                return name;
            } else {
                return "";
            }
        }

        String getFirst3Last3(String name) {
            int nameLength = name.length();
            if(nameLength > 6) {
                String firstThree = name.substring(0, 3);
                String lastThree = name.substring(nameLength - 3, nameLength);
                return firstThree + lastThree;
            } else {
                return name;
            }
        }

        int getMinRating(int sumLength) {
            boolean minRating = false;
            byte minRating1;
            if(sumLength <= 4) {
                minRating1 = 5;
            } else if(sumLength >= 5 && sumLength <= 7) {
                minRating1 = 4;
            } else if(sumLength >= 8 && sumLength <= 11) {
                minRating1 = 3;
            } else if(sumLength == 12) {
                minRating1 = 2;
            } else {
                minRating1 = 1;
            }

            return minRating1;
        }

        public boolean isEncodeEquals(String name1, String name2) {
            if(name1 != null && !"".equalsIgnoreCase(name1) && !" ".equalsIgnoreCase(name1)) {
                if(name2 != null && !"".equalsIgnoreCase(name2) && !" ".equalsIgnoreCase(name2)) {
                    if(name1.length() != 1 && name2.length() != 1) {
                        if(name1.equalsIgnoreCase(name2)) {
                            return true;
                        } else {
                            name1 = this.cleanName(name1);
                            name2 = this.cleanName(name2);
                            name1 = this.removeVowels(name1);
                            name2 = this.removeVowels(name2);
                            name1 = this.removeDoubleConsonants(name1);
                            name2 = this.removeDoubleConsonants(name2);
                            name1 = this.getFirst3Last3(name1);
                            name2 = this.getFirst3Last3(name2);
                            if(Math.abs(name1.length() - name2.length()) >= 3) {
                                return false;
                            } else {
                                int sumLength = Math.abs(name1.length() + name2.length());
                                boolean minRating = false;
                                int minRating1 = this.getMinRating(sumLength);
                                int count = this.leftToRightThenRightToLeftProcessing(name1, name2);
                                return count >= minRating1;
                            }
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        boolean isVowel(String letter) {
            return letter.equalsIgnoreCase("E") || letter.equalsIgnoreCase("A") || letter.equalsIgnoreCase("O") || letter.equalsIgnoreCase("I") || letter.equalsIgnoreCase("U");
        }

        int leftToRightThenRightToLeftProcessing(String name1, String name2) {
            char[] name1Char = name1.toCharArray();
            char[] name2Char = name2.toCharArray();
            int name1Size = name1.length() - 1;
            int name2Size = name2.length() - 1;
            String name1LtRStart = "";
            String name1LtREnd = "";
            String name2RtLStart = "";
            String name2RtLEnd = "";

            for(int strA = 0; strA < name1Char.length && strA <= name2Size; ++strA) {
                name1LtRStart = name1.substring(strA, strA + 1);
                name1LtREnd = name1.substring(name1Size - strA, name1Size - strA + 1);
                name2RtLStart = name2.substring(strA, strA + 1);
                name2RtLEnd = name2.substring(name2Size - strA, name2Size - strA + 1);
                if(name1LtRStart.equals(name2RtLStart)) {
                    name1Char[strA] = 32;
                    name2Char[strA] = 32;
                }

                if(name1LtREnd.equals(name2RtLEnd)) {
                    name1Char[name1Size - strA] = 32;
                    name2Char[name2Size - strA] = 32;
                }
            }

            String var13 = (new String(name1Char)).replaceAll("\\s+", "");
            String strB = (new String(name2Char)).replaceAll("\\s+", "");
            return var13.length() > strB.length()?Math.abs(6 - var13.length()):Math.abs(6 - strB.length());
        }

        String removeAccents(String accentedWord) {
            if(accentedWord == null) {
                return null;
            } else {
                StringBuilder sb = new StringBuilder();
                int n = accentedWord.length();

                for(int i = 0; i < n; ++i) {
                    char c = accentedWord.charAt(i);
                    int pos = "ÀàÈèÌìÒòÙùÁáÉéÍíÓóÚúÝýÂâÊêÎîÔôÛûŶŷÃãÕõÑñÄäËëÏïÖöÜüŸÿÅåÇçŐőŰű".indexOf(c);
                    if(pos > -1) {
                        sb.append("AaEeIiOoUuAaEeIiOoUuYyAaEeIiOoUuYyAaOoNnAaEeIiOoUuYyAaCcOoUu".charAt(pos));
                    } else {
                        sb.append(c);
                    }
                }

                return sb.toString();
            }
        }

        String removeDoubleConsonants(String name) {
            String replacedName = name.toUpperCase();
            String[] arr$ = DOUBLE_CONSONANT;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
                String dc = arr$[i$];
                if(replacedName.contains(dc)) {
                    String singleLetter = dc.substring(0, 1);
                    replacedName = replacedName.replace(dc, singleLetter);
                }
            }

            return replacedName;
        }

        String removeVowels(String name) {
            String firstLetter = name.substring(0, 1);
            name = name.replaceAll("A", "");
            name = name.replaceAll("E", "");
            name = name.replaceAll("I", "");
            name = name.replaceAll("O", "");
            name = name.replaceAll("U", "");
            name = name.replaceAll("\\s{2,}\\b", " ");
            return this.isVowel(firstLetter)?firstLetter + name:name;
        }
}
