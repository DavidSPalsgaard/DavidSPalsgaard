package dk.binumre.startlist

import org.w3c.dom.Element

// DOM-hjælpere der matcher på lokalt navn, så XML-navnerum (IOF 3.0, OOXML) er ligegyldige.

internal fun Element?.child(name: String): Element? {
    if (this == null) return null
    var n = firstChild
    while (n != null) {
        if (n is Element && (n.localName ?: n.nodeName) == name) return n
        n = n.nextSibling
    }
    return null
}

internal fun Element?.children(name: String): List<Element> {
    if (this == null) return emptyList()
    val out = mutableListOf<Element>()
    var n = firstChild
    while (n != null) {
        if (n is Element && (n.localName ?: n.nodeName) == name) out += n
        n = n.nextSibling
    }
    return out
}

internal fun Element?.path(vararg names: String): Element? {
    var e = this
    for (n in names) e = e.child(n) ?: return null
    return e
}

internal fun Element?.text(): String = this?.textContent?.trim() ?: ""
