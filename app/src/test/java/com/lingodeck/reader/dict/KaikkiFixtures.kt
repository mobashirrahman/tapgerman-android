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

    /**
     * The conjugation table of `gehen`, as Kaikki sends it: one JSON object per cell.
     *
     * Real payload, trimmed to the rows a table is built from and with the `table-tags` and
     * `inflection-template` markers dropped — those describe the template rather than a form, and
     * `KaikkiParser` filters them out, so leaving them in would only test that the filter is
     * absent.
     *
     * Stored as JSONL rather than as a Kotlin literal because these are the values the table is
     * supposed to come out of; a hand-written fixture would agree with whatever the builder does,
     * which is the mistake the `sagte`/`sagen` fixture already exists to avoid.
     */
    val GEHEN_FORMS: String =
        """
        {"form": "7 strong", "tags": ["class"], "source": "conjugation"}
        {"form": "sein", "tags": ["auxiliary"], "source": "conjugation"}
        {"form": "gehen", "tags": ["infinitive"], "source": "conjugation"}
        {"form": "gehend", "tags": ["participle", "present"], "source": "conjugation"}
        {"form": "gegangen", "tags": ["participle", "past"], "source": "conjugation"}
        {"form": "gehe", "tags": ["first-person", "indicative", "present", "singular"], "source": "conjugation"}
        {"form": "gehen", "tags": ["first-person", "indicative", "plural", "present"], "source": "conjugation"}
        {"form": "gehe", "tags": ["first-person", "singular", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "gehen", "tags": ["first-person", "plural", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "gehst", "tags": ["indicative", "present", "second-person", "singular"], "source": "conjugation"}
        {"form": "geht", "tags": ["indicative", "plural", "present", "second-person"], "source": "conjugation"}
        {"form": "gehest", "tags": ["second-person", "singular", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "gehet", "tags": ["plural", "second-person", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "geht", "tags": ["indicative", "present", "singular", "third-person"], "source": "conjugation"}
        {"form": "gehen", "tags": ["indicative", "plural", "present", "third-person"], "source": "conjugation"}
        {"form": "gehe", "tags": ["singular", "subjunctive", "subjunctive-i", "third-person"], "source": "conjugation"}
        {"form": "gehen", "tags": ["plural", "subjunctive", "subjunctive-i", "third-person"], "source": "conjugation"}
        {"form": "ging", "tags": ["first-person", "indicative", "preterite", "singular"], "source": "conjugation"}
        {"form": "gingen", "tags": ["first-person", "indicative", "plural", "preterite"], "source": "conjugation"}
        {"form": "ginge", "tags": ["first-person", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "gingen", "tags": ["first-person", "plural", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "gingst", "tags": ["indicative", "preterite", "second-person", "singular"], "source": "conjugation"}
        {"form": "gingt", "tags": ["indicative", "plural", "preterite", "second-person"], "source": "conjugation"}
        {"form": "gingest", "tags": ["second-person", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "gingst", "tags": ["second-person", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "ginget", "tags": ["plural", "second-person", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "gingt", "tags": ["plural", "second-person", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "ging", "tags": ["indicative", "preterite", "singular", "third-person"], "source": "conjugation"}
        {"form": "gingen", "tags": ["indicative", "plural", "preterite", "third-person"], "source": "conjugation"}
        {"form": "ginge", "tags": ["singular", "subjunctive", "subjunctive-ii", "third-person"], "source": "conjugation"}
        {"form": "gingen", "tags": ["plural", "subjunctive", "subjunctive-ii", "third-person"], "source": "conjugation"}
        {"form": "geh", "tags": ["imperative", "second-person", "singular"], "source": "conjugation"}
        {"form": "gehe", "tags": ["imperative", "second-person", "singular"], "source": "conjugation"}
        {"form": "geht", "tags": ["imperative", "plural", "second-person"], "source": "conjugation"}
        {"form": "7 strong", "tags": ["class"], "source": "conjugation"}
        {"form": "sein", "tags": ["auxiliary"], "source": "conjugation"}
        {"form": "bin gegangen", "tags": ["first-person", "indicative", "multiword-construction", "perfect", "singular"], "source": "conjugation"}
        {"form": "sind gegangen", "tags": ["first-person", "indicative", "multiword-construction", "perfect", "plural"], "source": "conjugation"}
        {"form": "sei gegangen", "tags": ["first-person", "multiword-construction", "perfect", "singular", "subjunctive"], "source": "conjugation"}
        {"form": "seien gegangen", "tags": ["first-person", "multiword-construction", "perfect", "plural", "subjunctive"], "source": "conjugation"}
        {"form": "bist gegangen", "tags": ["indicative", "multiword-construction", "perfect", "second-person", "singular"], "source": "conjugation"}
        {"form": "seid gegangen", "tags": ["indicative", "multiword-construction", "perfect", "plural", "second-person"], "source": "conjugation"}
        {"form": "seist gegangen", "tags": ["multiword-construction", "perfect", "second-person", "singular", "subjunctive"], "source": "conjugation"}
        {"form": "seiest gegangen", "tags": ["multiword-construction", "perfect", "second-person", "singular", "subjunctive"], "source": "conjugation"}
        {"form": "seiet gegangen", "tags": ["multiword-construction", "perfect", "plural", "second-person", "subjunctive"], "source": "conjugation"}
        {"form": "ist gegangen", "tags": ["indicative", "multiword-construction", "perfect", "singular", "third-person"], "source": "conjugation"}
        {"form": "sind gegangen", "tags": ["indicative", "multiword-construction", "perfect", "plural", "third-person"], "source": "conjugation"}
        {"form": "sei gegangen", "tags": ["multiword-construction", "perfect", "singular", "subjunctive", "third-person"], "source": "conjugation"}
        {"form": "seien gegangen", "tags": ["multiword-construction", "perfect", "plural", "subjunctive", "third-person"], "source": "conjugation"}
        {"form": "war gegangen", "tags": ["first-person", "indicative", "multiword-construction", "pluperfect", "singular"], "source": "conjugation"}
        {"form": "waren gegangen", "tags": ["first-person", "indicative", "multiword-construction", "pluperfect", "plural"], "source": "conjugation"}
        {"form": "wäre gegangen", "tags": ["first-person", "multiword-construction", "pluperfect", "singular", "subjunctive"], "source": "conjugation"}
        {"form": "wären gegangen", "tags": ["first-person", "multiword-construction", "pluperfect", "plural", "subjunctive"], "source": "conjugation"}
        {"form": "warst gegangen", "tags": ["indicative", "multiword-construction", "pluperfect", "second-person", "singular"], "source": "conjugation"}
        {"form": "wart gegangen", "tags": ["indicative", "multiword-construction", "pluperfect", "plural", "second-person"], "source": "conjugation"}
        {"form": "wärst gegangen", "tags": ["multiword-construction", "pluperfect", "second-person", "singular", "subjunctive"], "source": "conjugation"}
        {"form": "wärest gegangen", "tags": ["multiword-construction", "pluperfect", "second-person", "singular", "subjunctive"], "source": "conjugation"}
        {"form": "wärt gegangen", "tags": ["multiword-construction", "pluperfect", "plural", "second-person", "subjunctive"], "source": "conjugation"}
        {"form": "wäret gegangen", "tags": ["multiword-construction", "pluperfect", "plural", "second-person", "subjunctive"], "source": "conjugation"}
        {"form": "war gegangen", "tags": ["indicative", "multiword-construction", "pluperfect", "singular", "third-person"], "source": "conjugation"}
        {"form": "waren gegangen", "tags": ["indicative", "multiword-construction", "pluperfect", "plural", "third-person"], "source": "conjugation"}
        {"form": "wäre gegangen", "tags": ["multiword-construction", "pluperfect", "singular", "subjunctive", "third-person"], "source": "conjugation"}
        {"form": "wären gegangen", "tags": ["multiword-construction", "pluperfect", "plural", "subjunctive", "third-person"], "source": "conjugation"}
        {"form": "gehen werden", "tags": ["future", "future-i", "infinitive", "multiword-construction"], "source": "conjugation"}
        {"form": "werde gehen", "tags": ["first-person", "future", "future-i", "multiword-construction", "singular", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werden gehen", "tags": ["first-person", "future", "future-i", "multiword-construction", "plural", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werdest gehen", "tags": ["future", "future-i", "multiword-construction", "second-person", "singular", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werdet gehen", "tags": ["future", "future-i", "multiword-construction", "plural", "second-person", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werde gehen", "tags": ["future", "future-i", "multiword-construction", "singular", "subjunctive", "subjunctive-i", "third-person"], "source": "conjugation"}
        {"form": "werden gehen", "tags": ["future", "future-i", "multiword-construction", "plural", "subjunctive", "subjunctive-i", "third-person"], "source": "conjugation"}
        {"form": "werde gehen", "tags": ["first-person", "future", "future-i", "indicative", "multiword-construction", "singular"], "source": "conjugation"}
        {"form": "werden gehen", "tags": ["first-person", "future", "future-i", "indicative", "multiword-construction", "plural"], "source": "conjugation"}
        {"form": "würde gehen", "tags": ["first-person", "future", "future-i", "multiword-construction", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "würden gehen", "tags": ["first-person", "future", "future-i", "multiword-construction", "plural", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "wirst gehen", "tags": ["future", "future-i", "indicative", "multiword-construction", "second-person", "singular"], "source": "conjugation"}
        {"form": "werdet gehen", "tags": ["future", "future-i", "indicative", "multiword-construction", "plural", "second-person"], "source": "conjugation"}
        {"form": "würdest gehen", "tags": ["future", "future-i", "multiword-construction", "second-person", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "würdet gehen", "tags": ["future", "future-i", "multiword-construction", "plural", "second-person", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "wird gehen", "tags": ["future", "future-i", "indicative", "multiword-construction", "singular", "third-person"], "source": "conjugation"}
        {"form": "werden gehen", "tags": ["future", "future-i", "indicative", "multiword-construction", "plural", "third-person"], "source": "conjugation"}
        {"form": "würde gehen", "tags": ["future", "future-i", "multiword-construction", "singular", "subjunctive", "subjunctive-ii", "third-person"], "source": "conjugation"}
        {"form": "würden gehen", "tags": ["future", "future-i", "multiword-construction", "plural", "subjunctive", "subjunctive-ii", "third-person"], "source": "conjugation"}
        {"form": "gegangen sein werden", "tags": ["future", "future-ii", "infinitive", "multiword-construction"], "source": "conjugation"}
        {"form": "werde gegangen sein", "tags": ["first-person", "future", "future-ii", "multiword-construction", "singular", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werden gegangen sein", "tags": ["first-person", "future", "future-ii", "multiword-construction", "plural", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werdest gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "second-person", "singular", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werdet gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "plural", "second-person", "subjunctive", "subjunctive-i"], "source": "conjugation"}
        {"form": "werde gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "singular", "subjunctive", "subjunctive-i", "third-person"], "source": "conjugation"}
        {"form": "werden gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "plural", "subjunctive", "subjunctive-i", "third-person"], "source": "conjugation"}
        {"form": "werde gegangen sein", "tags": ["first-person", "future", "future-ii", "indicative", "multiword-construction", "singular"], "source": "conjugation"}
        {"form": "werden gegangen sein", "tags": ["first-person", "future", "future-ii", "indicative", "multiword-construction", "plural"], "source": "conjugation"}
        {"form": "würde gegangen sein", "tags": ["first-person", "future", "future-ii", "multiword-construction", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "würden gegangen sein", "tags": ["first-person", "future", "future-ii", "multiword-construction", "plural", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "wirst gegangen sein", "tags": ["future", "future-ii", "indicative", "multiword-construction", "second-person", "singular"], "source": "conjugation"}
        {"form": "werdet gegangen sein", "tags": ["future", "future-ii", "indicative", "multiword-construction", "plural", "second-person"], "source": "conjugation"}
        {"form": "würdest gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "second-person", "singular", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "würdet gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "plural", "second-person", "subjunctive", "subjunctive-ii"], "source": "conjugation"}
        {"form": "wird gegangen sein", "tags": ["future", "future-ii", "indicative", "multiword-construction", "singular", "third-person"], "source": "conjugation"}
        {"form": "werden gegangen sein", "tags": ["future", "future-ii", "indicative", "multiword-construction", "plural", "third-person"], "source": "conjugation"}
        {"form": "würde gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "singular", "subjunctive", "subjunctive-ii", "third-person"], "source": "conjugation"}
        {"form": "würden gegangen sein", "tags": ["future", "future-ii", "multiword-construction", "plural", "subjunctive", "subjunctive-ii", "third-person"], "source": "conjugation"}
        """.trimIndent()

    /** `https://kaikki.org/dictionary/German/meaning/s/sa/sagen.jsonl` */
    val SAGEN: String =
        """
        {"word":"sagen","pos":"verb","senses":[{"glosses":["to say (to pronounce; communicate verbally)"],"tags":["transitive","weak"],"examples":[{"text":"Ich habe nicht verstanden, was sie gesagt hat.","english":"I didn't understand what she said."}]},{"glosses":["to tell (to inform (someone) verbally)"],"tags":["ditransitive","weak"]},{"glosses":["to tell (to inform (someone) verbally)","to tell (to inform an authority)"],"tags":["childish","ditransitive","weak"]}],"sounds":[{"ipa":"ˈzaːɡn"}]}
        """.trimIndent()

    /**
     * The declension table of `Haus`, as Kaikki sends it.
     *
     * Real payload, kept whole. The plural rows are all tagged `definite` and the singulars are
     * not, which is not a quirk of the fixture but how Wiktionary marks a table that needs an
     * article in its dative plural — so trimming rows here would remove the only plural data there
     * is.
     */
    val HAUS_FORMS: String =
        """
        {"form": "Haus", "tags": ["nominative", "singular"], "source": "declension"}
        {"form": "Häuser", "tags": ["definite", "nominative", "plural"], "source": "declension"}
        {"form": "Hauses", "tags": ["genitive", "singular"], "source": "declension"}
        {"form": "Häuser", "tags": ["definite", "genitive", "plural"], "source": "declension"}
        {"form": "Haus", "tags": ["dative", "singular"], "source": "declension"}
        {"form": "Hause", "tags": ["dative", "singular"], "source": "declension"}
        {"form": "Häusern", "tags": ["dative", "definite", "plural"], "source": "declension"}
        {"form": "Haus", "tags": ["accusative", "singular"], "source": "declension"}
        {"form": "Häuser", "tags": ["accusative", "definite", "plural"], "source": "declension"}
        """.trimIndent()

    /**
     * The declension table of `Kind`, as Kaikki sends it.
     *
     * Real payload, kept whole. The plural rows are all tagged `definite` and the singulars are
     * not, which is not a quirk of the fixture but how Wiktionary marks a table that needs an
     * article in its dative plural — so trimming rows here would remove the only plural data there
     * is.
     */
    val KIND_FORMS: String =
        """
        {"form": "Kind", "tags": ["nominative", "singular"], "source": "declension"}
        {"form": "Kinder", "tags": ["definite", "nominative", "plural"], "source": "declension"}
        {"form": "Kindes", "tags": ["genitive", "singular"], "source": "declension"}
        {"form": "Kinds", "tags": ["genitive", "singular"], "source": "declension"}
        {"form": "Kinder", "tags": ["definite", "genitive", "plural"], "source": "declension"}
        {"form": "Kind", "tags": ["dative", "singular"], "source": "declension"}
        {"form": "Kinde", "tags": ["dative", "singular"], "source": "declension"}
        {"form": "Kindern", "tags": ["dative", "definite", "plural"], "source": "declension"}
        {"form": "Kind", "tags": ["accusative", "singular"], "source": "declension"}
        {"form": "Kinder", "tags": ["accusative", "definite", "plural"], "source": "declension"}
        """.trimIndent()

    /**
     * `Haus`'s `etymology_text`, verbatim.
     *
     * Kept whole rather than trimmed to the tree, because the last lines are the ones the parser
     * must *not* read as stages — the sentence beginning "From Middle High German" and the
     * "Cognate with" list. A fixture cut down to the answer would pass a parser that gets this
     * wrong.
     */
    val HAUS_ETYMOLOGY: String = "Etymology tree\nProto-Indo-European *(s)kewH-der.?\nProto-Germanic *hūsą\nProto-West Germanic *hūs\nOld High German hūs\nMiddle High German hūs\nGerman Haus\nFrom Middle High German hūs, from Old High German hūs, from Proto-West Germanic *hūs, from Proto-Germanic *hūsą.\nCognate with Old Frisian hūs, Low German Hus, Huus, Dutch huis, Icelandic hús, Faroese hús, Danish hus, Norwegian hus, Swedish hus, English house. Doublet of House."

    /**
     * `gehen`'s `etymology_text`, verbatim.
     *
     * Kept whole rather than trimmed to the tree, because the last lines are the ones the parser
     * must *not* read as stages — the sentence beginning "From Middle High German" and the
     * "Cognate with" list. A fixture cut down to the answer would pass a parser that gets this
     * wrong.
     */
    val GEHEN_ETYMOLOGY: String = "Etymology tree\nProto-Indo-European *ǵʰeh₁-der.\nProto-Germanic *gāną\nProto-West Germanic *gān\nOld High German gān\nProto-Indo-European *ǵʰengʰ-der.\nProto-Germanic *ganganą\nProto-West Germanic *gangan\nOld High German gangan\nMiddle High German gān\nGerman gehen\nFrom Middle High German gān, gēn, from Old High German gān, gēn, from Proto-West Germanic *gān, from Proto-Germanic *gāną, from Proto-Indo-European *ǵʰeh₁- (“to leave”).\nCognate with Dutch gaan, Low German gaan, gahn, English go, Swedish and Danish gå, Yiddish גיין (geyn).\nThe form gēn instead of gān is of Bavarian origin, but many dialects of Central and Low German have -e- (from earlier -ei-) or ei in the 2nd and 3rd person singular present, in keeping with the Proto-Germanic irregular conjugation. The -h- was introduced into the spelling by analogy with sehen, in which it had become mute but was retained in spelling.\nForms such as gingen, gegangen etc. derive from Old High German gangan, from Proto-West Germanic *gangan, from Proto-Germanic *ganganą, from Proto-Indo-European *ǵʰengʰ- (“to walk”)."

}
