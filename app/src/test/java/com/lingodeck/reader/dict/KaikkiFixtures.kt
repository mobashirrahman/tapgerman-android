package com.lingodeck.reader.dict

/**
 * Real kaikki.org payloads, trimmed to the fields the parser reads.
 *
 * `SAGTE` is the record the app gets when the reader taps a past-tense verb; `SAGEN` is the
 * record it fetches next because the first one's senses carry `form_of: sagen`. Between them
 * they are the whole conjugated-verb path, and they are kept as data rather than rebuilt as
 * inline JSON so a parser change cannot quietly be tested against a payload the server never
 * sends.
 */
internal object KaikkiFixtures {

    /** `https://kaikki.org/dictionary/German/meaning/s/sa/sagte.jsonl` */
    val SAGTE: String =
        """
        {"word":"sagte","pos":"verb","senses":[{"form_of":[{"word":"sagen"}],"glosses":["inflection of sagen:","first/third-person singular preterite"],"tags":["first-person","form-of","preterite","singular","third-person"],"links":[["sagen","sagen#German"]]},{"form_of":[{"word":"sagen"}],"glosses":["inflection of sagen:","first/third-person singular subjunctive II"],"tags":["first-person","form-of","singular","subjunctive-ii","third-person"],"links":[["sagen","sagen#German"]]}]}
        """.trimIndent()

    /** `https://kaikki.org/dictionary/German/meaning/s/sa/sagen.jsonl` */
    val SAGEN: String =
        """
        {"word":"sagen","pos":"verb","senses":[{"glosses":["to say (to pronounce; communicate verbally)"],"tags":["transitive","weak"],"examples":[{"text":"Ich habe nicht verstanden, was sie gesagt hat.","english":"I didn't understand what she said."}]},{"glosses":["to tell (to inform (someone) verbally)"],"tags":["ditransitive","weak"]},{"glosses":["to tell (to inform (someone) verbally)","to tell (to inform an authority)"],"tags":["childish","ditransitive","weak"]}],"sounds":[{"ipa":"ˈzaːɡn"}]}
        """.trimIndent()
}
