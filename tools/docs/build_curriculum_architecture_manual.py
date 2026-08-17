from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "deliverables" / "architecture-2026-08-14" / "Poincare_Curriculumarchitectuur_en_Uitrolhandleiding_Leerjaar_1_tot_6.docx"

BLUE = "2E74B5"
BLUE_DARK = "1F4D78"
NAVY = "203748"
GOLD = "F5C400"
MUTED = "5E6E7E"
PALE_BLUE = "E8EEF5"
PALE_GOLD = "FFF6CC"
WHITE = "FFFFFF"
GRID = "AAB7C4"


def set_cell_shading(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=80, start=120, bottom=80, end=120):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_width(cell, dxa):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(dxa))
    tc_w.set(qn("w:type"), "dxa")


def set_table_geometry(table, widths):
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    tbl_pr = table._tbl.tblPr
    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:w"), str(sum(widths)))
    tbl_w.set(qn("w:type"), "dxa")
    tbl_ind = tbl_pr.find(qn("w:tblInd"))
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), "120")
    tbl_ind.set(qn("w:type"), "dxa")
    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)
    for row in table.rows:
        tr_pr = row._tr.get_or_add_trPr()
        cant_split = OxmlElement("w:cantSplit")
        tr_pr.append(cant_split)
        for idx, cell in enumerate(row.cells):
            set_cell_width(cell, widths[idx])
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def set_run_font(run, size=None, color=None, bold=None, italic=None, name="Calibri"):
    run.font.name = name
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), name)
    if size is not None:
        run.font.size = Pt(size)
    if color is not None:
        run.font.color.rgb = RGBColor.from_string(color)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def add_field(paragraph, field):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = field
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.extend([begin, instr, separate, end])


def add_page_number(paragraph):
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = paragraph.add_run("Pagina ")
    set_run_font(run, size=9, color=MUTED)
    add_field(paragraph, "PAGE")


def style_document(doc):
    section = doc.sections[0]
    section.top_margin = Inches(0.75)
    section.bottom_margin = Inches(0.7)
    section.left_margin = Inches(1.0)
    section.right_margin = Inches(1.0)
    section.header_distance = Inches(0.3)
    section.footer_distance = Inches(0.18)

    normal = doc.styles["Normal"]
    normal.font.name = "Calibri"
    normal.font.size = Pt(11)
    normal._element.rPr.rFonts.set(qn("w:ascii"), "Calibri")
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), "Calibri")
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.25

    for style_name, size, color, before, after in (
        ("Heading 1", 16, BLUE, 18, 10),
        ("Heading 2", 13, BLUE, 14, 7),
        ("Heading 3", 12, BLUE_DARK, 10, 5),
    ):
        style = doc.styles[style_name]
        style.font.name = "Calibri"
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(color)
        style._element.rPr.rFonts.set(qn("w:ascii"), "Calibri")
        style._element.rPr.rFonts.set(qn("w:hAnsi"), "Calibri")
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True


def add_cover(doc):
    section = doc.sections[0]
    header = section.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = header.add_run("POINCARÉ  •  CURRICULUMARCHITECTUUR")
    set_run_font(run, size=8.5, color=MUTED, bold=True)
    footer = section.footer.paragraphs[0]
    add_page_number(footer)

    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(82)

    kicker = doc.add_paragraph()
    kicker.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = kicker.add_run("NORMATIEF PLAYBOOK")
    set_run_font(run, size=10, color=GOLD, bold=True)
    kicker.paragraph_format.space_after = Pt(18)

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("Curriculumarchitectuur\nen uitrolhandleiding")
    set_run_font(run, size=30, color=NAVY, bold=True)
    title.paragraph_format.space_after = Pt(10)

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = subtitle.add_run("Van leerjaar 1 naar een consistente leerjaar 1–6-architectuur")
    set_run_font(run, size=15, color=BLUE_DARK)
    subtitle.paragraph_format.space_after = Pt(34)

    table = doc.add_table(rows=4, cols=2)
    set_table_geometry(table, [2700, 6660])
    meta = [
        ("Versie", "14 augustus 2026"),
        ("Status", "Normatief bouw-, redactie- en releasecontract"),
        ("Bereik", "Curriculum Room, canonieke data, Android, verhalen, puzzels en volgende leerjaren"),
        ("Bronbasis", "Gebruikersfeedback, gerealiseerde fixes, V2-modellen/validatoren en release 2026-08-14-90d07e434fd8"),
    ]
    for row, (label, value) in zip(table.rows, meta):
        set_cell_shading(row.cells[0], PALE_BLUE)
        p = row.cells[0].paragraphs[0]
        r = p.add_run(label)
        set_run_font(r, size=9.5, color=BLUE_DARK, bold=True)
        p = row.cells[1].paragraphs[0]
        r = p.add_run(value)
        set_run_font(r, size=9.5, color=NAVY)

    note = doc.add_paragraph()
    note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    note.paragraph_format.space_before = Pt(30)
    r = note.add_run("Kernregel: één canonieke snapshot, één herkenbare structuur, één toetsbaar contract.")
    set_run_font(r, size=11, color=BLUE_DARK, italic=True)


def add_running_section(doc):
    section = doc.add_section(WD_SECTION.NEW_PAGE)
    section.top_margin = Inches(0.75)
    section.bottom_margin = Inches(0.7)
    section.left_margin = Inches(1.0)
    section.right_margin = Inches(1.0)
    section.header_distance = Inches(0.3)
    section.footer_distance = Inches(0.18)
    section.header.is_linked_to_previous = False
    section.footer.is_linked_to_previous = False
    header = section.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = header.add_run("POINCARÉ  |  Curriculumarchitectuur en uitrolhandleiding")
    set_run_font(r, size=8.5, color=MUTED, bold=True)
    p_pr = header._p.get_or_add_pPr()
    border = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "8")
    bottom.set(qn("w:space"), "5")
    bottom.set(qn("w:color"), PALE_BLUE)
    border.append(bottom)
    p_pr.append(border)
    add_page_number(section.footer.paragraphs[0])


def add_heading(doc, text, level=1):
    return doc.add_paragraph(text, style=f"Heading {level}")


def add_para(doc, text, bold_prefix=None):
    p = doc.add_paragraph()
    if bold_prefix and text.startswith(bold_prefix):
        r = p.add_run(bold_prefix)
        set_run_font(r, bold=True, color=NAVY)
        p.add_run(text[len(bold_prefix):])
    else:
        p.add_run(text)
    return p


def add_bullets(doc, items, level=0):
    for item in items:
        p = doc.add_paragraph(style="List Bullet" if level == 0 else "List Bullet 2")
        p.paragraph_format.left_indent = Inches(0.375 + 0.25 * level)
        p.paragraph_format.first_line_indent = Inches(-0.188)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.25
        p.add_run(item)


def add_numbers(doc, items):
    numbering = doc.part.numbering_part.element
    num_ids = [int(node.get(qn("w:numId"))) for node in numbering.findall(qn("w:num"))]
    num_id = max(num_ids, default=0) + 1
    num = OxmlElement("w:num")
    num.set(qn("w:numId"), str(num_id))
    abstract = OxmlElement("w:abstractNumId")
    abstract.set(qn("w:val"), "7")
    num.append(abstract)
    override = OxmlElement("w:lvlOverride")
    override.set(qn("w:ilvl"), "0")
    start = OxmlElement("w:startOverride")
    start.set(qn("w:val"), "1")
    override.append(start)
    num.append(override)
    numbering.append(num)
    for item in items:
        p = doc.add_paragraph(style="List Number")
        p_pr = p._p.get_or_add_pPr()
        num_pr = p_pr.find(qn("w:numPr"))
        if num_pr is None:
            num_pr = OxmlElement("w:numPr")
            p_pr.append(num_pr)
        ilvl = OxmlElement("w:ilvl")
        ilvl.set(qn("w:val"), "0")
        num_id_node = OxmlElement("w:numId")
        num_id_node.set(qn("w:val"), str(num_id))
        num_pr.extend([ilvl, num_id_node])
        p.paragraph_format.left_indent = Inches(0.375)
        p.paragraph_format.first_line_indent = Inches(-0.188)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.25
        p.add_run(item)


def add_callout(doc, title, text, fill=PALE_GOLD):
    table = doc.add_table(rows=1, cols=1)
    set_table_geometry(table, [9360])
    cell = table.cell(0, 0)
    set_cell_shading(cell, fill)
    p = cell.paragraphs[0]
    r = p.add_run(title + "  ")
    set_run_font(r, size=10.5, color=BLUE_DARK, bold=True)
    r = p.add_run(text)
    set_run_font(r, size=10.5, color=NAVY)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def add_table(doc, headers, rows, widths):
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    set_table_geometry(table, widths)
    for idx, header in enumerate(headers):
        cell = table.rows[0].cells[idx]
        set_cell_shading(cell, PALE_BLUE)
        p = cell.paragraphs[0]
        r = p.add_run(header)
        set_run_font(r, size=9.5, color=BLUE_DARK, bold=True)
    for row_values in rows:
        row = table.add_row()
        for idx, value in enumerate(row_values):
            p = row.cells[idx].paragraphs[0]
            r = p.add_run(value)
            set_run_font(r, size=9.25, color=NAVY)
    set_table_geometry(table, widths)
    return table


def page_break(doc):
    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)


def build():
    doc = Document()
    style_document(doc)
    add_cover(doc)
    add_running_section(doc)

    add_heading(doc, "1. Beslissingskader")
    add_callout(doc, "Uitkomst", "Nieuwe inhoud wordt niet meer gebouwd vanuit losse schermfixes, maar vanuit één canoniek data-, pedagogisch-, renderer- en releasecontract.")
    add_heading(doc, "Documentgezag", 2)
    add_numbers(doc, [
        "Actuele modellen en validatoren bepalen wat technisch geldig is.",
        "Het architectuurplaybook bepaalt hoe het product moet worden gebouwd.",
        "Het Generic Description-document bewaart de brede kwaliteitsprincipes.",
        "Goedgekeurde leerjaarsbronnen bepalen labels en sequenties.",
        "Audits en handoffs zijn gedateerd bewijs; legacycode en oude prompts zijn alleen context.",
    ])
    add_heading(doc, "Interpretatie", 2)
    add_bullets(doc, [
        "‘Moet’, ‘exact’ en ‘nooit’ zijn normatieve eisen.",
        "Aantallen, hashes en auditresultaten zijn snapshots en worden per release vervangen.",
        "Legacyvelden mogen compatibiliteit bewaren, maar nooit zichtbare taxonomie bepalen.",
        "Een voorbeeldfout activeert controle van de volledige pool, generatorregel, locale en toetsvariant.",
    ])

    page_break(doc)
    add_heading(doc, "2. Eén bron en één release")
    add_para(doc, "De Curriculum Room en de reproduceerbare bestanden in curriculum-library vormen samen de canonieke redactionele bron. Android-assets zijn uitsluitend release-uitvoer.")
    add_table(doc, ["Stap", "Verplicht resultaat"], [
        ("1. Redactie", "Nieuwe of verbeterde data met stabiele ID's, status en bronmodus."),
        ("2. Snapshot", "Basisdata + geldige editorwijzigingen samengevoegd tot één effectieve snapshot."),
        ("3. Validatie", "Schema-, pedagogische, asset- en pariteitscontroles zonder blocking fouten."),
        ("4. Release", "Release-ID, checksum, tellingen en shard-/assethashes."),
        ("5. Publicatie", "Curriculum Room publiceert exact die snapshot."),
        ("6. Android", "Exact dezelfde snapshot wordt geëxporteerd, getest en gebouwd."),
        ("7. Toestel", "Installatie alleen na expliciete toestemming; daarna een gerichte smoke-test."),
    ], [1700, 7660])
    add_callout(doc, "Releaseblokker", "Curriculum Room en Android tonen niet dezelfde release-ID en checksum, of editorwijzigingen vallen buiten de checksum.", fill="FCE8E6")

    page_break(doc)
    add_heading(doc, "3. Canonieke domeinstructuur")
    add_table(doc, ["Niveau", "Contract"], [
        ("Curriculum", "Versie, locales, categorieën en globale release-informatie."),
        ("Categorie", "Stabiele ID, manifestlabel, volgorde en gespecialiseerde route."),
        ("Subcategorie", "Leerjaarbereik, subcategoriesequentie en tegelbeschikbaarheid."),
        ("Thema/unit", "Inhoudelijk herkenbare groep; geen generieke ‘Leerjaar 1’-dubbelgroep."),
        ("Les", "Eén toetsbare vaardigheidscluster met stabiele ID en lesstap."),
        ("Theorie", "Geordende pagina's, één hoofdconcept per pagina."),
        ("Oefenen", "Eén zichtbaar deel met afzonderlijke reeksen 1..n."),
        ("Toets", "Exact één finaletest en enige bron voor de lespoort."),
    ], [2000, 7360])
    add_heading(doc, "Stabiele identiteit", 2)
    add_bullets(doc, [
        "Titel-, tekst-, visual- en volgordecorrecties behouden de bestaande ID.",
        "Nieuwe inhoud krijgt een nieuwe ID; samenvoegen/splitsen krijgt een expliciete mapping.",
        "Profiel, voortgang, pogingen, beloningen, verhaalstatus en sync-ID's blijven behouden.",
        "Een onbekend veld of legacyrecord blokkeert migratie in plaats van stil te verdwijnen.",
    ])

    page_break(doc)
    add_heading(doc, "4. Taxonomie en ontgrendeling")
    add_table(doc, ["ID", "Zichtbaar label", "Route"], [
        ("language_skills", "Taalvaardigheid", "Kernlessen + verhalen"),
        ("spelling_grammar", "Spelling & grammatica", "Kernlessen"),
        ("number_sense", "Rekenen & getalbegrip", "Kernlessen"),
        ("measurement_geometry", "Meten & meetkunde", "Kernlessen"),
        ("world_orientation", "Wereldoriëntatie", "Kernlessen"),
        ("logic_puzzles", "Logica & puzzels", "Gespecialiseerde puzzels"),
    ], [2400, 3400, 3560])
    add_heading(doc, "Twee sequentieniveaus", 2)
    add_bullets(doc, [
        "Subcategoriesequentie: een hogere stap opent na voltooiing van alle lagere beschikbare stappen binnen dezelfde categorie en hetzelfde leerjaar.",
        "Lesstap: ordent lessen binnen één subcategorie.",
        "Gelijke lesstappen zijn parallel en openen samen; de volgende stap wacht op alle parallelle lessen.",
        "Reeds geopende of voltooide inhoud wordt na migratie niet opnieuw vergrendeld.",
        "Leerjaar 1 gebruikt bronkolommen B/D als overschrijving, E voor subcategorie en H voor lesstap; K overschrijft de samenvatting.",
    ])

    page_break(doc)
    add_heading(doc, "5. Drieledige kernles")
    add_callout(doc, "Zichtbaar contract", "Theorie → Oefenen → Toets. Oefenen mag meerdere reeksen bevatten; een reeks is geen vierde deel.")
    add_table(doc, ["Deel", "Datavorm", "Harde eisen"], [
        ("Theorie", "exact één THEORY, eerst", "Leerdoel, uitleg, regel, voorbeeld, één concept per pagina."),
        ("Oefenen", "één of meer EXERCISES", "Eén set per fase, doorlopende seriesIndex, begeleid + zelfstandig."),
        ("Toets", "exact één MASTERY_TEST, laatst", "Nieuwe equivalente items, geen hints, volledige mastery-dekking."),
    ], [1900, 2700, 4760])
    add_heading(doc, "Compatibiliteit", 2)
    add_para(doc, "Historische fasetypen worden alleen nog ingelezen voor oude data en voortgang. Nieuwe lessen schrijven uitsluitend THEORY, EXERCISES en MASTERY_TEST. De adapter verdwijnt pas bij nul legacylessen en een geslaagde V2-runtimesuite.")
    add_heading(doc, "Foutentolerantie", 2)
    add_bullets(doc, [
        "Oefenen: eerste helft twee toegelaten fouten; tweede helft één. Bij een oneven aantal krijgt de strengere helft één reeks meer.",
        "Toets: geen hints; feedback na afloop.",
    ])

    page_break(doc)
    add_heading(doc, "6. Theorie en kindtaal")
    add_heading(doc, "Theoriepagina", 2)
    add_bullets(doc, [
        "Eén term, concept, strategie of handeling per pagina.",
        "Wereldoriëntatie en woordenschat: ieder nieuw begrip krijgt een eigen definitie, voorbeeld en passende visual.",
        "De openingspagina bevat alleen doel en hoogstens een korte woordenlijst; geen samengeperste definities.",
        "Vanaf pagina 2 kan het kind terug; inhoudstabs worden niet boven de actieve pagina herhaald.",
        "Schrijven, rekenen en meetkunde krijgen meerdere uitgewerkte voorbeelden wanneer één voorbeeld onvoldoende variatie toont.",
    ])
    add_heading(doc, "Vraag en antwoord", 2)
    add_bullets(doc, [
        "Leerjaar 1 wordt gelezen als een kind van zes; leerjaar 2 als een kind van zeven.",
        "Eén korte, concrete opdracht; alle context is zichtbaar of hoorbaar.",
        "Single-choice heeft exact één juist antwoord; opties zijn uniek en grammaticaal vergelijkbaar.",
        "Geen nummerprefix, editorcode, voorbeeldkaart, feedbacktekst of leerkrachtinstructie in een optie.",
        "Bij meerdere geldige antwoorden: vraag vernauwen of interactietype wijzigen.",
        "TTS leest de oefenvraag, niet telkens de algemene lesopdracht.",
    ])
    add_callout(doc, "Voorbeeld", "Gebruik ‘Hoe zeg je hallo tegen een vriend?’ in plaats van een abstracte vraag over ‘informele Franse begroeting’.")

    page_break(doc)
    add_heading(doc, "7. Oefenen, generatie en transparantie")
    add_table(doc, ["Bronmodus", "Betekenis", "Verplichte redactionele zichtbaarheid"], [
        ("CURATED", "Vaste redactionele items", "Vraag, opties, correct antwoord, feedback en status."),
        ("GENERATED", "Runtime-items uit contract", "Criteria, grenzen, versie, seed, regels en echt voorbeeld."),
    ], [1700, 2600, 5060])
    add_heading(doc, "Generatorcontract", 2)
    add_bullets(doc, [
        "Generator-ID, semantische versie en implementatieversie.",
        "Deterministisch seed- en reproduceerbaarheidsbeleid.",
        "Selectiecriteria, moeilijkheid, woordenschat, mastery-tags en representaties.",
        "Regels voor juiste antwoorden en afleiders; uitsluiting van recente toetsitems.",
        "Eén werkelijk genereerbare leerlingvraag met opties, juist antwoord en feedback.",
        "V1/V2 is geen editorfilter; herkomst mag wel als Manueel, Genereert vragen of Technisch zichtbaar zijn.",
    ])
    add_callout(doc, "Pattern fix", "Corrigeer bij een fout de volledige pool, generatorregel, locale, redactionele voorbeelden, toetsblueprint en rendererfallback; voeg daarna een regressietest toe.")

    page_break(doc)
    add_heading(doc, "8. Interactie, audio en layout")
    add_heading(doc, "Oefenscherm", 2)
    add_bullets(doc, [
        "Toetsenbord of antwoordopties staan onderaan met vaste bottom margin.",
        "Vraag en oefencontent zijn verticaal gecentreerd in de resterende ruimte.",
        "Geen overflow: pagina's splitsen of verantwoord scrollen, niet onbeperkt tekst verkleinen.",
        "Leerjaar 1: eerste tik leest een optie voor en markeert voorlopig; tweede tik bevestigt.",
        "Voortgang registreert pas na bevestiging.",
    ])
    add_heading(doc, "Dictee", 2)
    add_para(doc, "Volgorde: één ontbrekende letter → meerdere letters → volledig woord → korte zin → langere zin. Iedere vraag bevat uitspreekbare inhoud, locale en inputtype. Het aangepaste toetsenbord verschijnt bij schrijfinput.")
    add_heading(doc, "Visual en spraak", 2)
    add_bullets(doc, [
        "Visual heeft stabiele ID, doel, alt-tekst, bron/licentie, palette en assethash.",
        "Spacetiles is voor categorie-/subcategorie-SVG's: transparant, alleen witte dikke lijnen, inhoudelijk herkenbaar.",
        "SVG-generatie gebruikt standaard alleen stijl + beschrijving; learningIntent uitsluitend bij aangevinkte lescontext.",
        "Zichtbare en gesproken tekst zijn apart; uitspraakfixes horen in de normalizer.",
        "Een zichtbare /m/ wordt m zonder slashes, vet en PlanetGold.",
    ])

    page_break(doc)
    add_heading(doc, "9. Verhalen en puzzels")
    add_heading(doc, "Verhalen", 2)
    add_table(doc, ["Wereld", "Stijl", "Vaste regels"], [
        ("Kleine verhaaltjes", "warme aquarel", "Boeknummer + AVI; numeriek gesorteerd."),
        ("Nova", "Space travellers", "Afzonderlijke boeken en hoofdstukken."),
        ("Robbie", "videogame", "Afzonderlijke hoofdstukken; passende unieke beelden."),
    ], [2200, 2200, 4960])
    add_bullets(doc, [
        "Hiërarchie: Wereld → Reeks → Boek → Hoofdstuk → Pagina → optionele vraag.",
        "Hoofdstukthumbnail is 1:1; beeldtop en titel zijn uitgelijnd.",
        "Lange tekst wordt verdeeld of het beeld proportioneel kleiner; tekst loopt nooit uit het frame.",
        "Afronden gaat terug naar het hoofdstukoverzicht.",
        "Verhalen blijven onaangeraakt bij een curriculumexport met --skip-stories; hashes moeten gelijk blijven.",
    ])
    add_heading(doc, "Puzzels", 2)
    add_para(doc, "Puzzels volgen geen Theorie–Oefenen–Toets. Alleen een puzzel met renderer, configuratie, toegankelijk inputpad, voortgang, beloningscontract en tests is klikbaar. Lege klassieke puzzelsubcategorieën en verouderde tegels worden verwijderd; puzzels tonen XP.")

    page_break(doc)
    add_heading(doc, "10. Uitrol van een volgend leerjaar")
    add_table(doc, ["Fase", "Werk", "Go/no-go"], [
        ("A. Bron", "Doelen, bronverwijzingen, labels, E/H-equivalenten, duplicaten en samenvattingen.", "Alle records zijn één-op-één verklaard."),
        ("B. Verticale proef", "Eén volledige les per hoofdcategorie, inclusief webeditor en Android-renderer.", "Alle renderers en progressiepaden werken."),
        ("C. Batch", "Kleine samenhangende subcategorie, kindreview en pattern fixes.", "Geen blocking inhouds- of schemaregels."),
        ("D. Pariteit", "Effectieve snapshot, audits, Curriculum Room, Android-export en tests.", "Eén release-ID/checksum."),
        ("E. Acceptatie", "Doeldekking, retentie, menselijke observatie waar nodig en device smoke.", "Inhoud is aantoonbaar bruikbaar, niet alleen aanwezig."),
    ], [1200, 4920, 3240])
    add_heading(doc, "Eerstvolgende toepassing: leerjaar 2", 2)
    add_numbers(doc, [
        "Maak een goedgekeurde leerjaar-2-bron met expliciete overschrijf- en sequentievelden.",
        "Draai een dry-run en controleer alle mappings, parallelle stappen en vermoedelijke duplicaten.",
        "Bouw per hoofdcategorie één verticale proef en review alles als zevenjarige leerling.",
        "Migreer daarna per subcategorie met voortgangs- en pariteitstest na elke batch.",
        "Publiceer pas wanneer web en Android dezelfde releasehash tonen.",
    ])

    page_break(doc)
    add_heading(doc, "11. Kwaliteit en Definition of Done")
    add_heading(doc, "Automatisch blokkerend", 2)
    add_bullets(doc, [
        "Schema, verplichte velden, unieke ID's en geldige relaties.",
        "THEORY → EXERCISES 1..n → MASTERY_TEST en doorlopende seriesIndex.",
        "Exact één correct single-choice-antwoord en geen verboden placeholder-/editortekst.",
        "Generatorversie, seed, criteria en echt voorbeeld.",
        "Renderer-capability, locale/fallback, visual/audio en assetbestaan.",
        "Geen cycli in sequenties/prerequisites; geen onverwachte catalogus-, asset- of APK-groei.",
        "Gelijke Curriculum Room- en Android-releasehash.",
    ])
    add_heading(doc, "Pedagogisch en handmatig", 2)
    add_bullets(doc, [
        "Begrijpt het kind alle woorden en weet het onmiddellijk wat te doen?",
        "Is één hoofdconcept actief en is alle context aanwezig?",
        "Is het antwoord eenduidig en de representatie eerder uitgelegd?",
        "Is de moeilijkheidssprong klein genoeg en feedback bruikbaar?",
        "Smoke-test minstens één les per renderer/bronmodus, plus dictee, trace, wereldconcept, doeltaal, verhaal en actieve puzzel.",
        "Test offline hervatten, portret/landschap, telefoon/tablet en alle afrondbestemmingen.",
    ])
    add_callout(doc, "Definition of Done", "Bron en backup zijn aantoonbaar; pattern fix, ID-/voortgangsbehoud, blocking tests en releasebewijs zijn rond; publicatie/installatie bleven binnen toestemming.")

    page_break(doc)
    add_heading(doc, "12. Feedbacktrace en actuele risico's")
    add_table(doc, ["Feedbackpatroon", "Permanente regel", "Bewijs"], [
        ("Web ≠ app", "Manifest + één snapshot zijn canoniek.", "Pariteitsaudit/hash"),
        ("Lange oefenbank", "Afzonderlijke reeksen onder één Oefenen.", "seriesIndex/UI"),
        ("Complexe eerste pagina", "Eén concept per theoriepagina.", "Kindreview"),
        ("Dubbelzinnig antwoord", "Eén juist antwoord of ander interactietype.", "Poollint"),
        ("Dictee zonder audio/input", "Audio + locale + inputtype verplicht.", "Renderer/device"),
        ("Onwerkende tegel", "Alleen capability-backed routes klikbaar.", "Capability-audit"),
        ("Ondoorzichtige generator", "Criteria, versie, seed en echt voorbeeld.", "Room-test"),
        ("Volgorde onlogisch", "Subcategorie- en lesstap expliciet.", "Gate-test"),
        ("Installatie verschilt", "Exact artifact en hash in handoff.", "Smoke-log"),
    ], [2600, 4600, 2160])
    add_heading(doc, "Actuele overgangsrisico's", 2)
    add_bullets(doc, [
        "657 legacylessen blijven achter de adapter; nieuwe content mag dit aantal niet verhogen.",
        "Workflowlabels van de Curriculum Room en V2-publicatiestatus hebben één expliciete mapping nodig.",
        "Inhoud bestaan, technisch geldig, pedagogisch gereviewd en gepubliceerd moeten apart gerapporteerd blijven.",
        "Offline TTS en verhaalbeelden vereisen blijvende APK-/assetgroottepoorten.",
    ])

    page_break(doc)
    add_heading(doc, "13. Snapshot en referenties")
    add_heading(doc, "Status op 14 augustus 2026", 2)
    add_table(doc, ["Metriek", "Waarde"], [
        ("Categorieën / subcategorieën", "6 / 56"),
        ("Lesrecords in webbibliotheek", "348"),
        ("V2-kernlessen / verhaallessen", "223 / 125"),
        ("Oefensets / vaste oefeningen", "2.356 / 21.000"),
        ("Boeken / hoofdstukken / verhaalpagina's", "19 / 130 / 2.037"),
        ("Resterende legacylessen", "657"),
        ("Android-release", "2026-08-14-90d07e434fd8"),
        ("Releasechecksum", "90d07e434fd8f4191ad89acfacb8e8cf1860d437886d694f5feecd2d90473c7c"),
    ], [3300, 6060])
    add_heading(doc, "Canonieke referenties", 2)
    add_bullets(doc, [
        "CURRICULUM_ARCHITECTURE_PLAYBOOK.md — volledig normatief contract.",
        "GENERIC_DESCRIPTION_CURRICULUM.md — product- en kwaliteitsprincipes.",
        "SECTIONED_LESSON_ARCHITECTURE.md — exact lesdata-/runtimecontract.",
        "CURRICULUM_STRUCTURE_GRADE1_MIGRATION.md — migratie- en extrapolatiemethode.",
        "LessonV2Models.kt + LessonCatalogV2Validator.kt — technisch model.",
        "library-meta.json + release-metadata-v1.json — snapshotbewijs.",
    ])
    add_callout(doc, "Volgende actie", "Maak de leerjaar-2-bron expliciet, voer een dry-run uit en valideer één verticale les per hoofdcategorie vóór bulkproductie.")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUT)
    print(OUT)


if __name__ == "__main__":
    build()
