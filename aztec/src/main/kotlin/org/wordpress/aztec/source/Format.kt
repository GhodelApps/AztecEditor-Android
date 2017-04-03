package org.wordpress.aztec.source

import android.text.TextUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Matcher
import java.util.regex.Pattern




object Format {

    // list of block elements
    private val block = "div|br|blockquote|ul|ol|li|p|h1|h2|h3|h4|h5|h6|iframe|hr"

    private val iframePlaceholder = "iframe-replacement-0x0"

    fun addSourceEditorFormatting(content: String): String {
//        // rename iframes to prevent encoding the inner HTML
        var html = replaceAll(content, "iframe", iframePlaceholder)
//        html = Jsoup.parseBodyFragment(html).body().html()
        html = replaceAll(html, iframePlaceholder, "iframe")

        val doc = Jsoup.parse(html).outputSettings(Document.OutputSettings().prettyPrint(false))

        doc.select("*")
                .filter { !it.hasText() && it.tagName() == "span" && it.childNodes().size == 0 }
                .forEach { it.remove() }
//
//        //remove newline around all non block elements
//        val newlineToTheLeft = replaceAll(html, "(?<!</?($block)>)\n<((?!/?($block)).*?)>", "<$2>")
//        val newlineToTheRight = replaceAll(newlineToTheLeft, "<(/?(?!$block).)>\n(?!</?($block)>)", "<$1>")
//        var fixBrNewlines = replaceAll(newlineToTheRight, "([\t ]*)(<br>)(?!\n)", "$1$2\n$1")
//        fixBrNewlines = replaceAll(fixBrNewlines, ">([\t ]*)(<br>)", ">\n$1$2")
//
//        return fixBrNewlines.trim()

        html = replaceAll(doc.body().html(), "<p>(?:<br ?/?>|\u00a0|\uFEFF| )*</p>", "")
//        html = replaceAll(html, iframePlaceholder, "iframe")

        return toCalypsoHtml(html).trim()

    }

    fun removeSourceEditorFormatting(html: String): String {
        val htmlWitthouSourceEdtorFormatting = toRealHtml(html)

        val doc = Jsoup.parse(htmlWitthouSourceEdtorFormatting.replace("\n","")).outputSettings(Document.OutputSettings().prettyPrint(false))

        doc.select("*")
                .filter { !it.hasText() && !it.isBlock && !it.tagName().equals("br") && it.childNodes().size == 0 }
                .forEach { it.remove() }

        return doc.body().html()
    }

    private fun replaceAll(content: String, pattern: String, replacement: String): String {
        val p = Pattern.compile(pattern)
        val m = p.matcher(content)
        return m.replaceAll(replacement)
    }

    //Takes HTML as is and formats it for source viewer by replacing <p> with \n\n and <br> with \n
    fun toCalypsoHtml(htmlContent: String): String {
        var content = htmlContent
        if (TextUtils.isEmpty(content.trim { it <= ' ' })) {
            // Just whitespace, null, or undefined
            return ""
        }

        var preserve_linebreaks = false
        var preserve_br = false

        var p: Pattern
        var m: Matcher
        var sb: StringBuffer

        // Protect pre|script tags
        if (content.contains("<pre") || content.contains("<script")) {
            preserve_linebreaks = true
            p = Pattern.compile("<(pre|script)[^>]*>[\\s\\S]+?</\\u0001>")
            m = p.matcher(content)
            sb = StringBuffer()
            if (m.find()) {
                m.appendReplacement(sb, replaceAll(content.substring(m.start(), m.end()), "<br ?/?>(\\r\\n|\\n)?", "<wp-line-break>"))
                m.appendReplacement(sb, replaceAll(content.substring(m.start(), m.end()), "</?p( [^>]*)?>(\\r\\n|\\n)?", "<wp-line-break>"))
                m.appendReplacement(sb, replaceAll(content.substring(m.start(), m.end()), "\\r?\\n", "<wp-line-break>"))
            }
            m.appendTail(sb)
            content = sb.toString()
        }

        // keep <br> tags inside captions and remove line breaks
        if (content.contains("[caption")) {
            preserve_br = true
            p = Pattern.compile("\\[caption[\\s\\S]+?\\[/caption\\]")
            m = p.matcher(content)
            sb = StringBuffer()
            if (m.find()) {
                val result = replaceAll(content.substring(m.start(), m.end()), "<br([^>]*)>", "<wp-temp-br$1>")
                m.appendReplacement(sb, replace(result, "[\\r\\n\\t]+", ""))
            }
            m.appendTail(sb)
            content = sb.toString()
        }

        // Pretty it up for the source editor
        val blocklist = "blockquote|ul|ol|li|table|thead|tbody|tfoot|tr|th|td|h[1-6]|fieldset"
        val blocklist1 = blocklist + "|div|p"
        val blocklist2 = blocklist + "|pre"

        content = replaceAll(content, "\\s*</($blocklist1)>\\s*", "</$1>\n")
        content = replaceAll(content, "\\s*<((?:$blocklist1)(?: [^>]*)?)>", "\n<$1>")

        // Mark </p> if it has any attributes.
        content = replaceAll(content, "(<p [^>]+>.*?)</p>", "$1</p#>")

        // Separate <div> containing <p>
        content = replaceAll(content, "(?i)<div( [^>]*)?>\\s*<p>", "<div$1>\n\n")

        // Remove <p> and <br />
        content = replaceAll(content, "(?i)\\s*<p>", "")
        content = replaceAll(content, "(?i)\\s*</p>\\s*", "\n\n")
        content = replaceAll(content, "\\n[\\s\\u00a0]+\\n", "\n\n")
        content = replaceAll(content, "(?i)\\s*<br ?/?>\\s*", "\n");

        // Fix some block element newline issues
        content = replaceAll(content, "\\s*<div", "\n<div")
        content = replaceAll(content, "</div>\\s*", "</div>\n")
        content = replaceAll(content, "(?i)\\s*\\[caption([^\\[]+)\\[/caption\\]\\s*", "\n\n[caption$1[/caption]\n\n")
        content = replaceAll(content, "caption\\]\\n\\n+\\[caption", "caption]\n\n[caption")

//        blocklist = "blockquote|ul|ol|li|table|thead|tbody|tfoot|tr|th|td|h[1-6]|pre|fieldset"
        content = replaceAll(content, "\\s*<((?:$blocklist2)(?: [^>]*)?)\\s*>", "\n<$1>")
        content = replaceAll(content, "\\s*</($blocklist2)>\\s*", "</$1>\n")

        content = replaceAll(content, "<li([^>]*)>", "\t<li$1>")

        if (content.contains("<option")) {
            content = replaceAll(content, "\\s*<option", "\n<option")
            content = replaceAll(content, "\\s*</select>", "\n</select>")
        }

        if (content.contains("<hr")) {
            content = replaceAll(content, "\\s*<hr( [^>]*)?>\\s*", "\n\n<hr$1>\n\n")
        }

        if (content.contains("<object")) {
            p = Pattern.compile("<object[\\s\\S]+?</object>")
            m = p.matcher(content)
            sb = StringBuffer()
            if (m.find()) {
                m.appendReplacement(sb, replace(content.substring(m.start(), m.end()), "[\\r\\n]+", ""))
            }
            m.appendTail(sb)
            content = sb.toString()
        }

        // Unmark special paragraph closing tags
        content = replaceAll(content, "</p#>", "</p>\n");
        content = replaceAll(content, "\\s*(<p [^>]+>[\\s\\S]*?</p>)", "\n$1");

        // Trim whitespace
        content = replaceAll(content, "^\\s+", "")
        content = replaceAll(content, "[\\s\\u00a0]+$", "")

        // put back the line breaks in pre|script
        if (preserve_linebreaks) {
            content = replaceAll(content, "<wp-line-break>", "\n")
        }

        // and the <br> tags in captions
        if (preserve_br) {
            content = replaceAll(content, "<wp-temp-br([^>]*)>", "<br$1>")
        }

        return content
    }

    fun toRealHtml(formattedHtml: String): String {
        var html = formattedHtml
        if (TextUtils.isEmpty(html.trim { it <= ' ' })) {
            // Just whitespace, null, or undefined
            return ""
        }

        var preserve_linebreaks = false
        var preserve_br = false

        var p: Pattern
        var m: Matcher
        var sb: StringBuffer

        val blocklist = "table|thead|tfoot|caption|col|colgroup|tbody|tr|td|th|div|dl|dd|dt|ul|ol|li|pre" +
                "|form|map|area|blockquote|address|math|style|p|h[1-6]|hr|fieldset|legend|section" +
                "|article|aside|hgroup|header|footer|nav|figure|details|menu|summary"


        if (html.contains("<object")) {
            p = Pattern.compile("<object[\\s\\S]+?</object>")
            m = p.matcher(html)
            sb = StringBuffer()
            while (m.find()) {
                m.appendReplacement(sb, replaceAll(html.substring(m.start(), m.end()), "[\\r\\n]+", ""))
            }
            m.appendTail(sb)
            html = sb.toString()
        }

        p = Pattern.compile("<[^<>]+>")
        m = p.matcher(html)
        sb = StringBuffer()
        while (m.find()) {
            m.appendReplacement(sb, replaceAll(html.substring(m.start(), m.end()), "[\\r\\n]+", ""))
        }
        m.appendTail(sb)
        html = sb.toString()

        // Protect pre|script tags
        if (html.contains("<pre") || html.contains("<script")) {
            preserve_linebreaks = true

            p = Pattern.compile("<(pre|script)[^>]*>[\\s\\S]+?</\\u0001>")
            m = p.matcher(html)
            sb = StringBuffer()
            while (m.find()) {
                m.appendReplacement(sb, replaceAll(html.substring(m.start(), m.end()), "(\\r\\n|\\n)", "<wp-line-break>"))
            }
            m.appendTail(sb)
            html = sb.toString()
        }

        // keep <br> tags inside captions and convert line breaks
        if (html.contains("[caption' )")) {
            preserve_br = true

            p = Pattern.compile("\\[caption[\\s\\S]+?\\[/caption\\]")
            m = p.matcher(html)
            sb = StringBuffer()
            while (m.find()) {
                // keep existing <br>
                m.appendReplacement(sb, replaceAll(html.substring(m.start(), m.end()), "<br([^>]*)>", "<wp-temp-br$1>"))

                // no line breaks inside HTML tags
                val p2 = Pattern.compile("<[a-zA-Z0-9]+( [^<>]+)?>")
                val content = html.substring(m.start(), m.end())
                val m2 = p2.matcher(content)
                val sb2 = StringBuffer()
                while (m2.find()) {
                    m2.appendReplacement(sb2, replace(content.substring(m2.start(), m2.end()), "[\\r\\n\\t]+", " "))
                }
                m2.appendTail(sb2)
                m.appendReplacement(sb, sb2.toString())

                // convert remaining line breaks to <br>
                m.appendReplacement(sb, replaceAll(html.substring(m.start(), m.end()), "\\s*\\n\\s*", "<wp-temp-br />"))
            }
            m.appendTail(sb)
            html = sb.toString()
        }

        html += "\n\n"


        html = replaceAll(html, "(?i)<br ?/?>\\s*<br ?/?>", "\n\n")
        html = replaceAll(html, "(?i)(<(?:$blocklist)(?: [^>]*)?>)", "\n$1")
        html = replaceAll(html, "(?i)(</(?:$blocklist)>)", "$1\n\n")


        html = replaceAll(html, "(?i)<hr( [^>]*)?>", "<hr$1>\n\n"); // hr is self closing block element

        html = replaceAll(html, "(?i)\\s*<option", "<option"); // No <p> or <br> around <option>
        html = replaceAll(html, "(?i)</option>\\s*", "</option>");
        html = replaceAll(html, "\\r\\n|\\r", "\n");
        html = replaceAll(html, "\\n\\s*\\n+", "\n\n");
        html = replaceAll(html, "([\\s\\S]+?)\\n\\n", "<p>$1</p>\n");
        html = replaceAll(html, "(?i)<p>\\s*?</p>", "");
        html = replaceAll(html, "(?i)<p>\\s*(</?(?:$blocklist)(?: [^>]*)?>)\\s*</p>", "$1");
        html = replaceAll(html, "(?i)<p>(<li.+?)</p>", "$1");
        html = replaceAll(html, "(?i)<p>\\s*<blockquote([^>]*)>", "<blockquote$1>");
        html = replaceAll(html, "(?i)</blockquote>\\s*</p>", "</blockquote>");
        html = replaceAll(html, "(?i)<p>\\s*(</?(?:$blocklist)(?: [^>]*)?>)", "$1");
        html = replaceAll(html, "(?i)(</?(?:$blocklist)(?: [^>]*)?>)\\s*</p>", "$1");
        html = replaceAll(html, "(?i)\\s*\\n", "<br>\n");
        html = replaceAll(html, "(?i)(</?(?:$blocklist)[^>]*>)\\s*<br ?/?>", "$1");
        html = replaceAll(html, "(?i)<br ?/?>(\\s*</?(?:p|li|div|dl|dd|dt|th|pre|td|ul|ol)>)", "$1");
        html = replaceAll(html, "(?i)(?:<p>|<br ?/?>)*\\s*\\[caption([^\\[]+)\\[/caption\\]\\s*(?:</p>|<br ?/?>)*", "[caption$1[/caption]")



        html = html.replace(Regex("(<(?:div|th|td|form|fieldset|dd)[^>]*>)(.*?)</p>"), { matchResult: MatchResult ->
            if (matchResult.groupValues[2].matches(Regex("<p( [^>]*)?>"))) {
                matchResult.groupValues[0]
            } else {
                matchResult.groupValues[1] + "<p>" + matchResult.groupValues[2] + "</p>"
            }
        })
//
//                html = replaceAll("(<(?:div|th|td|form|fieldset|dd)[^>]*>)(.*?)<\\/p>", function( a, b, c ) {
//                    if ( c.match( /<p( [^>]*)?>/ ) ) {
//                        return a;
//                    }
//
//                    return b + '<p>' + c + '</p>';
//                });

        // put back the line breaks in pre|script
        if (preserve_linebreaks) {
            html = replaceAll(html, "<wp-line-break>", "\n")
        }

        if (preserve_br) {
            html = replaceAll(html, "<wp-temp-br([^>]*)>", "<br$1>")
        }

        return html.trim()
    }

    private fun replace(content: String, pattern: String, replacement: String): String {
        val p = Pattern.compile(pattern)
        val m = p.matcher(content)
        return m.replaceFirst(replacement)
    }
}
