/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package top.lib.fasterc.aapt;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a row from a symbols file generated by {@code aapt}.
 */
public class RDotTxtEntry implements Comparable<RDotTxtEntry> {

    private static final Pattern TEXT_SYMBOLS_LINE = Pattern.compile("(\\S+) (\\S+) (\\S+) (.+)");
    public static final Function<String, RDotTxtEntry> TO_ENTRY = new Function<String, RDotTxtEntry>() {
        public RDotTxtEntry apply(String input) {
            Optional<RDotTxtEntry> entry = parse(input);
            Preconditions.checkNotNull(entry.isPresent(), "Could not parse R.txt entry: '%s'", input);
            return entry.get();
        }
    };
    // A symbols file may look like:
    //
    // int id placeholder 0x7f020000
    // int string debug_http_proxy_dialog_title 0x7f030004
    // int string debug_http_proxy_hint 0x7f030005
    // int string debug_http_proxy_summary 0x7f030003
    // int string debug_http_proxy_title 0x7f030002
    // int string debug_ssl_cert_check_summary 0x7f030001
    // int string debug_ssl_cert_check_title 0x7f030000
    //
    // Note that there are four columns of information:
    // - the type of the resource id (always seems to be int or int[], in
    // practice)
    // - the type of the resource
    // - the name of the resource
    // - the value of the resource id
    public final IdType idType;
    public final RType  type;
    public final String name;
    public       String idValue;
    public RDotTxtEntry(IdType idType, RType type, String name, String idValue) {
        this.idType = Preconditions.checkNotNull(idType);
        this.type = Preconditions.checkNotNull(type);
        this.name = Preconditions.checkNotNull(name);
        this.idValue = Preconditions.checkNotNull(idValue);
    }

    public static Optional<RDotTxtEntry> parse(String rDotTxtLine) {
        Matcher matcher = TEXT_SYMBOLS_LINE.matcher(rDotTxtLine);
        if (!matcher.matches()) {
            return Optional.absent();
        }

        IdType idType = IdType.from(matcher.group(1));
        RType type = RType.valueOf(matcher.group(2).toUpperCase());
        String name = matcher.group(3);
        String idValue = matcher.group(4);

        return Optional.of(new RDotTxtEntry(idType, type, name, idValue));
    }

    public RDotTxtEntry copyWithNewIdValue(String newIdValue) {
        return new RDotTxtEntry(idType, type, name, newIdValue);
    }

    /**
     * A collection of Resources should be sorted such that Resources of the
     * same type should be grouped together, and should be alphabetized within
     * that group.
     */
    public int compareTo(RDotTxtEntry that) {
        return ComparisonChain.start().compare(this.type, that.type).compare(this.name, that.name).result();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RDotTxtEntry)) {
            return false;
        }

        RDotTxtEntry that = (RDotTxtEntry) obj;
        return Objects.equal(this.type, that.type) && Objects.equal(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{type, name});
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(RDotTxtEntry.class).add("idType", idType).add("type", type).add("name", name).add("idValue", idValue.trim()).toString();
    }

    // Taken from http://developer.android.com/reference/android/R.html
    // TRANSITION for api level 19
    public enum RType {
        ANIM, ANIMATOR, ARRAY, ATTR, BOOL, COLOR, DIMEN, DRAWABLE, FRACTION, ID, INTEGER, INTERPOLATOR, LAYOUT, MENU, MIPMAP, PLURALS, RAW, STRING, STYLE, STYLEABLE, TRANSITION, XML;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public enum IdType {
        INT, INT_ARRAY;

        public static IdType from(String raw) {
            if (raw.equals("int")) {
                return INT;
            } else if (raw.equals("int[]")) {
                return INT_ARRAY;
            }
            throw new IllegalArgumentException(String.format("'%s' is not a valid ID type.", raw));
        }

        public String toString() {
            if (this.equals(INT)) {
                return "int";
            }
            return "int[]";
        }
    }
}
