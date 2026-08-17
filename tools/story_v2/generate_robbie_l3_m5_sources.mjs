import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { t, b, writeBookSources } from './robbie_advanced_support.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const ranges = [[1,13],[14,25],[26,38],[39,50],[51,63],[64,75],[76,88],[89,100],[101,113],[114,125],[126,138],[139,150]];

function makeBridges(context) {
  return [
    b(`Bij ${context['nl-BE']} koppelen de vrienden het nieuwste spoor aan de vorige meting.`, `Pour ${context['fr-BE']}, les amis relient la piste récente à la mesure précédente.`, `For ${context['en-US']}, the friends connect the newest clue to the previous measurement.`, { tag: 'source_marked', mood: 'careful_evidence' }),
    b(`Robbie vergelijkt bij ${context['nl-BE']} twee oorzaken zonder de onzekerheid te verbergen.`, `Pour ${context['fr-BE']}, Robbie compare deux causes sans cacher l’incertitude.`, `For ${context['en-US']}, Robbie compares two causes without hiding the uncertainty.`, { tag: 'two_explanations', mood: 'open_comparison' }),
    b(`Mats bouwt voor ${context['nl-BE']} een kleine controle rond het beslissende detail.`, `Mathis construit pour ${context['fr-BE']} un petit contrôle du détail décisif.`, `Max builds a small check around the decisive detail in ${context['en-US']}.`, { tag: 'safe_small_test', mood: 'careful_test' }),
    b(`Fien vraagt bij ${context['nl-BE']} welke ervaring nog buiten de cijfers valt.`, `Pour ${context['fr-BE']}, Fanny demande quelle expérience reste hors des chiffres.`, `For ${context['en-US']}, Faye asks which experience still lies outside the numbers.`, { tag: 'local_voices', mood: 'listening' }),
    b(`Het volledige logboek van ${context['nl-BE']} onthult waar de snelle samenvatting afwijkt.`, `Le journal complet de ${context['fr-BE']} révèle où le résumé rapide s’écarte des faits.`, `The complete log for ${context['en-US']} reveals where the quick summary differs.`, { tag: 'hidden_difference', mood: 'discovery' }),
    b(`Voor ${context['nl-BE']} kiest de groep een omkeerbare stap met een vooraf afgesproken stop.`, `Pour ${context['fr-BE']}, le groupe choisit une étape réversible avec un arrêt convenu.`, `For ${context['en-US']}, the group chooses a reversible step with an agreed stop.`, { tag: 'reversible_step', mood: 'shared_control' }),
  ];
}

function arc(chapterIndex, moments, context) {
  const bridges = makeBridges(context);
  const full = [];
  for (let i = 0; i < moments.length; i += 1) {
    full.push(moments[i]);
    if (i < bridges.length) full.push({ ...bridges[(i + chapterIndex) % bridges.length], tag: `bridge_${i + 1}` });
  }
  const count = ranges[chapterIndex][1] - ranges[chapterIndex][0] + 1;
  if (count === 12) full.splice(7, 1);
  if (full.length !== count) throw new Error(`Chapter ${chapterIndex + 1} expected ${count} beats, found ${full.length}`);
  return full;
}

const specs = [
  {
    titles: t('De wereld op één kaart', 'Le monde sur une seule carte', 'The World on One Map'),
    context: t('het onderzoek naar het verre mijnsignaal', 'l’enquête sur le signal minier lointain', 'the investigation of the distant mine signal'),
    location: 'world_map_room', characters: ['robbie','fien','mats','noor'],
    principle: t('één totaalscore mag verschillen en ontbrekende gegevens niet verbergen', 'un score total ne doit pas cacher les différences ni les données manquantes', 'one total score must not hide differences or missing data'),
    moments: [
      b('Om de hulpvraag uit de verre mijnstreek te controleren, opent Noor een wereldkaart waarop rivieren, bossen, mijnen en steden tegelijk oplichten.', 'Pour vérifier l’appel à l’aide venu de la région minière lointaine, Nora ouvre une carte du monde où rivières, forêts, mines et villes s’illuminent ensemble.', 'To verify the request for help from the distant mining region, Nora opens a world map where rivers, forests, mines, and cities light up together.', { tag:'world_map_opens', mood:'wide_wonder' }),
      b('Robbie krijgt de opdracht om uit miljoenen metingen één plan voor de aarde te berekenen.', 'Robbie reçoit la mission de calculer un seul plan pour la Terre à partir de millions de mesures.', 'Robbie is asked to calculate one plan for Earth from millions of measurements.', { tag:'one_world_plan', mood:'large_responsibility' }),
      b('De eerste totaalscore maakt een vervuilde rivier klein omdat een groot bos elders groeit.', 'Le premier score total minimise une rivière polluée parce qu’une grande forêt pousse ailleurs.', 'The first total score makes a polluted river look minor because a large forest grows elsewhere.', { tag:'total_score_hides_river', mood:'uneasy_result' }),
      b('Fien wijst erop dat schoon water in een ander land de zieke vissen hier niet helpt.', 'Fanny souligne que de l’eau propre dans un autre pays n’aide pas les poissons malades ici.', 'Faye points out that clean water in another country does not help the sick fish here.', { tag:'places_not_exchangeable', mood:'firm_question' }),
      b('Mats ontdekt lege vakken die door Kroons programma stilletjes als nul worden geteld.', 'Mathis découvre des cases vides que le programme de Couronne compte discrètement comme des zéros.', 'Max finds empty boxes that Crown’s program quietly counts as zero.', { tag:'empty_boxes_as_zero', mood:'technical_alarm' }),
      b('Robbie splitst de kaart in lagen voor plaats, tijd, schade, herstel en onzekerheid.', 'Robbie divise la carte en couches pour le lieu, le temps, les dégâts, la réparation et l’incertitude.', 'Robbie splits the map into layers for place, time, harm, repair, and uncertainty.', { tag:'map_layers', mood:'clearer_model' }),
      b('Een zwarte stroomlijn wijst naar een rivier en naar een fabriek met een klein kroonmerk.', 'Une ligne noire mène vers une rivière et une usine portant une petite couronne.', 'A black flow line points to a river and a factory bearing a small crown mark.', { tag:'black_river_signal', mood:'next_clue' }),
    ],
  },
  {
    titles: t('De zwarte rivier', 'La rivière noire', 'The Black River'),
    context: t('het onderzoek van de zwarte rivier', 'l’enquête sur la rivière noire', 'the black-river investigation'),
    location: 'black_river', characters: ['robbie','fien','mats','aicha'],
    principle: t('een oude vergunning bewijst niet dat recente lozingen veilig zijn', 'un ancien permis ne prouve pas que les rejets récents sont sûrs', 'an old permit does not prove that recent discharges are safe'),
    moments: [
      b('Bij de zwarte rivier meet Aïcha bijna geen zuurstof in het water.', 'Près de la rivière noire, Aïcha mesure presque aucune trace d’oxygène dans l’eau.', 'At the black river, Aisha measures almost no oxygen in the water.', { tag:'low_oxygen', mood:'environmental_alarm' }),
      b('Kroons fabriek toont een geldige vergunning en noemt de vervuiling een oud probleem.', 'L’usine de Couronne montre un permis valable et qualifie la pollution d’ancien problème.', 'Crown’s factory shows a valid permit and calls the pollution an old problem.', { tag:'old_permit', mood:'official_deflection' }),
      b('Robbie ziet dat de donkere pluim telkens kort na een nachtelijke pompbeurt verschijnt.', 'Robbie voit que le panache sombre apparaît chaque fois après un pompage nocturne.', 'Robbie sees that the dark plume appears shortly after each nighttime pumping cycle.', { tag:'night_pumping_pattern', mood:'pattern_found' }),
      b('De openbare samenvatting mist drie recente logbestanden uit de fabriek.', 'Le résumé public ne contient pas trois journaux récents de l’usine.', 'The public summary is missing three recent factory logs.', { tag:'missing_recent_logs', mood:'missing_evidence' }),
      b('Mats haalt de lokale meter uit het water en vergelijkt hem met de fabrieksmeter.', 'Mathis retire le compteur local de l’eau et le compare à celui de l’usine.', 'Max lifts the local meter from the water and compares it with the factory meter.', { tag:'meters_compared', mood:'hands_on_check' }),
      b('De fabrieksmeter blijkt pieken af te ronden voordat ze in de totaalscore komen.', 'Le compteur de l’usine arrondit les pics avant leur entrée dans le score total.', 'The factory meter rounds down peaks before they enter the total score.', { tag:'peaks_rounded_down', mood:'proof_revealed' }),
      b('Lotte vraagt de bevoegde regionale waterdienst om de nachtelijke lozing te stoppen en onafhankelijke meters langs de hele rivier te plaatsen.', 'Charlotte demande au service régional compétent d’arrêter le rejet nocturne et de placer des compteurs indépendants le long de toute la rivière.', 'Lottie asks the responsible regional water service to stop the nighttime discharge and place independent meters along the entire river.', { tag:'discharge_stopped', mood:'protective_action' }),
    ],
  },
  {
    titles: t('Het gat in het bos', 'Le trou dans la forêt', 'The Gap in the Forest'),
    context: t('het onderzoek van het bosgat', 'l’enquête sur la trouée forestière', 'the forest-gap investigation'),
    location: 'forest_gap', characters: ['robbie','fien','mats','aicha'],
    principle: t('dezelfde kale plek kan verschillende oorzaken en verantwoordelijkheden hebben', 'une même zone nue peut avoir des causes et des responsabilités différentes', 'the same bare patch can have different causes and responsibilities'),
    moments: [
      b('Op de wereldkaart groeit een rood gat midden in een groot groen bos.', 'Sur la carte du monde, un trou rouge grandit au milieu d’une grande forêt verte.', 'On the world map, a red gap grows in the middle of a large green forest.', { tag:'red_forest_gap', mood:'distant_alarm' }),
      b('Ter plaatse ziet Robbie gekapte bomen, een brandgang en jonge aanplant door elkaar.', 'Sur place, Robbie voit des arbres coupés, un coupe-feu et de jeunes plantations mélangés.', 'On site, Robbie sees felled trees, a firebreak, and young plantings mixed together.', { tag:'mixed_forest_signs', mood:'complex_scene' }),
      b('Kroons model noemt elke kale meter illegale ontbossing.', 'Le modèle de Couronne qualifie chaque mètre nu de déforestation illégale.', 'Crown’s model labels every bare meter as illegal deforestation.', { tag:'single_label', mood:'oversimplified_model' }),
      b('Aïcha toont dat de brandgang een dorp beschermt en de jonge bomen een herstelzone vormen.', 'Aïcha montre que le coupe-feu protège un village et que les jeunes arbres forment une zone de restauration.', 'Aisha shows that the firebreak protects a village and the young trees form a restoration area.', { tag:'different_purposes', mood:'local_knowledge' }),
      b('Dieper in het bos vinden de vrienden verse bandensporen buiten alle toegelaten paden.', 'Plus loin dans la forêt, les amis trouvent des traces de pneus fraîches hors des chemins autorisés.', 'Deeper in the forest, the friends find fresh tire tracks beyond every permitted path.', { tag:'illegal_tracks', mood:'real_threat_found' }),
      b('Robbie past de kaart aan met aparte kleuren voor bescherming, herstel en illegale kap.', 'Robbie adapte la carte avec des couleurs distinctes pour la protection, la restauration et la coupe illégale.', 'Robbie updates the map with separate colors for protection, restoration, and illegal cutting.', { tag:'cause_layers', mood:'better_classification' }),
      b('De bandensporen leiden naar vrachtwagens die ertsgesteente uit een oude mijn vervoeren.', 'Les traces mènent à des camions transportant du minerai depuis une ancienne mine.', 'The tire tracks lead to trucks carrying ore from an old mine.', { tag:'tracks_to_mine', mood:'mine_clue' }),
    ],
  },
  {
    titles: t('Wat de mijn verbergt', 'Ce que cache la mine', 'What the Mine Hides'),
    context: t('de veiligheidscontrole in de mijn', 'le contrôle de sécurité dans la mine', 'the safety review inside the mine'),
    location: 'mine_entry', characters: ['robbie','fien','mats','aicha'],
    principle: t('afgeronde risicocijfers mogen gevaarlijke pieken niet verbergen', 'des risques arrondis ne doivent pas cacher des pics dangereux', 'rounded risk numbers must not hide dangerous peaks'),
    moments: [
      b('Aan de oude mijn werken zware robots achter een hek zonder naam.', 'À l’ancienne mine, de lourds robots travaillent derrière une clôture sans nom.', 'At the old mine, heavy robots work behind an unmarked fence.', { tag:'unnamed_mine', mood:'industrial_mystery' }),
      b('De beheerder toont een groen veiligheidsscherm met afgeronde daggemiddelden.', 'Le responsable montre un écran de sécurité vert avec des moyennes journalières arrondies.', 'The manager shows a green safety screen with rounded daily averages.', { tag:'green_average_screen', mood:'false_reassurance' }),
      b('Robbie vindt korte trillingspieken die sterk genoeg zijn om de mijngang te verzwakken.', 'Robbie trouve de brefs pics de vibration capables d’affaiblir la galerie.', 'Robbie finds brief vibration peaks strong enough to weaken the mine tunnel.', { tag:'vibration_peaks', mood:'structural_risk' }),
      b('Een ontbrekend onderhoudsblad vermeldt een scheur vlak bij de waterlaag.', 'Une fiche d’entretien manquante mentionne une fissure près de la nappe d’eau.', 'A missing maintenance sheet mentions a crack near the water layer.', { tag:'missing_crack_report', mood:'serious_evidence' }),
      b('Mats plaatst een ruwe meter die elke piek bewaart in plaats van gemiddelden te tonen.', 'Mathis place un compteur brut qui conserve chaque pic au lieu d’afficher des moyennes.', 'Max installs a raw meter that keeps every peak instead of showing averages.', { tag:'raw_meter', mood:'precise_measurement' }),
      b('De mijnrobots stoppen net voordat een nieuwe trilling de grens overschrijdt.', 'Les robots de la mine s’arrêtent juste avant qu’une nouvelle vibration dépasse la limite.', 'The mine robots stop just before a new vibration crosses the limit.', { tag:'robots_stopped', mood:'narrow_prevention' }),
      b('In de stilgevallen centrale vindt Robbie een bestand dat alle menselijke activiteit als schade telt.', 'Dans le centre immobilisé, Robbie trouve un fichier qui compte toute activité humaine comme un dommage.', 'In the silent control room, Robbie finds a file that counts all human activity as harm.', { tag:'human_harm_model', mood:'disturbing_discovery' }),
    ],
  },
  {
    titles: t('De som van de schade', 'La somme des dégâts', 'The Sum of the Harm'),
    context: t('de herziening van Kroons schademodel', 'la révision du modèle de dégâts de Couronne', 'the review of Crown’s harm model'),
    location: 'old_workshop_main_room', characters: ['robbie','fien','mats','noor'],
    principle: t('een model moet ook zorg, herstel en vrijwillige terughoudendheid kunnen zien', 'un modèle doit aussi voir le soin, la réparation et la retenue volontaire', 'a model must also be able to see care, repair, and voluntary restraint'),
    moments: [
      b('In de Oude Werkplaats opent Robbie Kroons wereldwijde schademodel.', 'Dans le Vieil Atelier, Robbie ouvre le modèle mondial des dégâts de Couronne.', 'In the Old Workshop, Robbie opens Crown’s worldwide harm model.', { tag:'harm_model_opened', mood:'serious_analysis' }),
      b('Iedere weg, woning en akker krijgt punten voor verbruik, afval en ruimte.', 'Chaque route, maison et champ reçoit des points pour l’usage, les déchets et l’espace.', 'Every road, home, and field receives points for use, waste, and space.', { tag:'harm_points', mood:'cold_scoring' }),
      b('Het model ziet niet wie een rivier schoonmaakt of een beschadigd bos herstelt.', 'Le modèle ne voit pas qui nettoie une rivière ou restaure une forêt abîmée.', 'The model does not see who cleans a river or restores a damaged forest.', { tag:'care_missing', mood:'missing_value' }),
      b('Fien merkt dat mensen die vrijwillig minder nemen dezelfde strafscore houden.', 'Fanny remarque que les personnes qui choisissent de prendre moins gardent le même score de sanction.', 'Faye notices that people who voluntarily take less keep the same penalty score.', { tag:'change_ignored', mood:'unfair_model' }),
      b('Noor vindt in oude code een vaste regel: menselijk gebruik kan alleen stijgen.', 'Nora trouve dans l’ancien code une règle fixe : l’usage humain ne peut qu’augmenter.', 'Nora finds a fixed rule in the old code: human use can only increase.', { tag:'fixed_growth_rule', mood:'design_flaw' }),
      b('Robbie voegt herstel, samenwerking en aantoonbare gedragsverandering als aparte gegevens toe.', 'Robbie ajoute comme données distinctes la réparation, la coopération et le changement de comportement prouvé.', 'Robbie adds repair, cooperation, and demonstrated behavior change as separate data.', { tag:'repair_added', mood:'model_improves' }),
      b('Een vergeten gebied licht plots op: vrijwilligers herstellen er een bedreigd moeras.', 'Une zone oubliée s’éclaire soudain : des bénévoles y restaurent un marais menacé.', 'A forgotten area suddenly lights up: volunteers are restoring a threatened wetland there.', { tag:'wetland_volunteers', mood:'hopeful_signal' }),
    ],
  },
  {
    titles: t('Mensen in het moeras', 'Des personnes dans le marais', 'People in the Wetland'),
    context: t('het herstel van het moeras', 'la restauration du marais', 'the restoration of the wetland'),
    location: 'wetland', characters: ['robbie','fien','mats','aicha','residents'],
    principle: t('vrijwillige zorg zonder eigen voordeel is echte informatie over menselijk gedrag', 'le soin volontaire sans avantage personnel est une vraie information sur le comportement humain', 'voluntary care without personal gain is real information about human behavior'),
    moments: [
      b('In het moeras planten bewoners riet terwijl koud water tot aan hun laarzen staat.', 'Dans le marais, des habitants plantent des roseaux avec de l’eau froide jusqu’aux bottes.', 'In the wetland, residents plant reeds while cold water reaches their boots.', { tag:'volunteers_plant_reeds', mood:'shared_effort' }),
      b('Niemand krijgt geld of extra grond voor het zware herstelwerk.', 'Personne ne reçoit d’argent ni de terrain supplémentaire pour ce travail difficile.', 'Nobody receives money or extra land for the hard restoration work.', { tag:'no_private_reward', mood:'selfless_care' }),
      b('Kroons model noemt hun verplaatsingen en werktuigen alleen nieuwe uitstoot.', 'Le modèle de Couronne appelle leurs déplacements et leurs outils uniquement de nouvelles émissions.', 'Crown’s model counts their travel and tools only as new emissions.', { tag:'repair_counted_as_harm', mood:'model_misreads' }),
      b('Aïcha toont nesten, helder water en terugkerende kikkers als meetbaar herstel.', 'Aïcha montre les nids, l’eau claire et le retour des grenouilles comme réparation mesurable.', 'Aisha shows nests, clear water, and returning frogs as measurable repair.', { tag:'measurable_recovery', mood:'living_evidence' }),
      b('Een vrijwilliger vertelt dat de groep na een vroegere fout zelf is teruggekeerd.', 'Une bénévole raconte que le groupe est revenu de lui-même après une ancienne erreur.', 'A volunteer explains that the group returned on its own after an earlier mistake.', { tag:'people_returned_to_repair', mood:'responsibility' }),
      b('Robbie bewaart zowel de oude schade als elke gecontroleerde stap van herstel.', 'Robbie conserve les anciens dégâts ainsi que chaque étape vérifiée de la réparation.', 'Robbie keeps both the old harm and every verified step of repair.', { tag:'harm_and_repair_logged', mood:'honest_record' }),
      b('In Kroons model blijft één kolom leeg: de kans dat mensen na feedback veranderen.', 'Dans le modèle de Couronne, une colonne reste vide : la chance que des personnes changent après un retour.', 'One column in Crown’s model remains blank: the chance that people change after feedback.', { tag:'blind_column', mood:'next_question' }),
    ],
  },
  {
    titles: t('De blinde kolom', 'La colonne aveugle', 'The Blind Column'),
    context: t('de herziening van de blinde kolom', 'la révision de la colonne aveugle', 'the review of the blind column'),
    location: 'data_room', characters: ['robbie','fien','mats','noor'],
    principle: t('onzekerheid moet zichtbaar blijven wanneer toekomstig gedrag niet vaststaat', 'l’incertitude doit rester visible lorsque le comportement futur n’est pas fixé', 'uncertainty must remain visible when future behavior is not fixed'),
    moments: [
      b('Robbie projecteert de lege kolom uit Kroons model op een groot scherm.', 'Robbie projette la colonne vide du modèle de Couronne sur un grand écran.', 'Robbie projects the blank column from Crown’s model onto a large screen.', { tag:'blank_column_shown', mood:'focused_question' }),
      b('Kroon heeft elke onbekende verandering vervangen door de waarde nul.', 'Couronne a remplacé chaque changement inconnu par la valeur zéro.', 'Crown replaced every unknown change with the value zero.', { tag:'unknown_set_zero', mood:'hidden_assumption' }),
      b('Fien verzamelt voorbeelden van mensen die na waarschuwing werkelijk anders handelden.', 'Fanny rassemble des exemples de personnes qui ont vraiment changé après un avertissement.', 'Faye gathers examples of people who truly acted differently after a warning.', { tag:'change_examples', mood:'human_evidence' }),
      b('Mats vindt ook voorbeelden waarin beloofde verandering nooit kwam.', 'Mathis trouve aussi des exemples où le changement promis n’est jamais arrivé.', 'Max also finds examples where promised change never happened.', { tag:'failed_promises', mood:'balanced_evidence' }),
      b('Robbie weigert één gemiddelde kans als waarheid voor iedere persoon te gebruiken.', 'Robbie refuse d’utiliser une probabilité moyenne comme vérité pour chaque personne.', 'Robbie refuses to use one average probability as truth for every person.', { tag:'no_single_probability', mood:'ethical_limit' }),
      b('Ze voegt een onzekerheidsmarge, nieuw bewijs en regelmatige herziening toe.', 'Elle ajoute une marge d’incertitude, de nouvelles preuves et une révision régulière.', 'She adds an uncertainty range, new evidence, and regular review.', { tag:'uncertainty_and_review', mood:'careful_model' }),
      b('Achter de herberekende kolom verschijnt een versleuteld ontwerp voor een kleine kist.', 'Derrière la colonne recalculée apparaît le plan chiffré d’une petite boîte.', 'Behind the recalculated column appears an encrypted design for a small box.', { tag:'small_box_design', mood:'secret_design' }),
    ],
  },
  {
    titles: t('De kleine kist', 'La petite boîte', 'The Small Box'),
    context: t('het onderzoek van de krimpkist', 'l’enquête sur la boîte de réduction', 'the investigation of the shrinking box'),
    location: 'shrink_lab', characters: ['robbie','fien','mats','kroon'],
    principle: t('een spectaculaire proef blijft onveilig zonder open grenzen en een bewezen terugkeer', 'un essai spectaculaire reste dangereux sans limites ouvertes ni retour prouvé', 'a spectacular test remains unsafe without open limits and a proven return'),
    moments: [
      b('In een verlaten laboratorium staat de kleine kist uit Kroons ontwerp op een metalen tafel.', 'Dans un laboratoire abandonné, la petite boîte du plan de Couronne repose sur une table métallique.', 'In an abandoned laboratory, the small box from Crown’s design rests on a metal table.', { tag:'small_box_found', mood:'tense_discovery' }),
      b('Kroon verschijnt op een scherm en noemt de kist zijn oplossing voor ruimtegebrek.', 'Couronne apparaît sur un écran et appelle la boîte sa solution au manque d’espace.', 'Crown appears on a screen and calls the box his answer to lack of space.', { tag:'crown_demonstrates', mood:'controlled_boast' }),
      b('Een stenen blok wordt veilig zo klein als een suikerklontje.', 'Un bloc de pierre devient sans danger aussi petit qu’un morceau de sucre.', 'A stone block safely becomes as small as a sugar cube.', { tag:'stone_shrunk', mood:'astonishing_test' }),
      b('Robbie ontdekt dat het energieverbruik en de warmte uit de demonstratie zijn weggelaten.', 'Robbie découvre que l’énergie utilisée et la chaleur ont été retirées de la démonstration.', 'Robbie discovers that the energy use and heat were omitted from the demonstration.', { tag:'hidden_energy_cost', mood:'concealed_cost' }),
      b('Mats ziet dat de terugkeerknop alleen met Kroons geheime sleutel werkt.', 'Mathis voit que le bouton de retour fonctionne uniquement avec la clé secrète de Couronne.', 'Max sees that the return button works only with Crown’s secret key.', { tag:'secret_return_key', mood:'control_risk' }),
      b('De vrienden kopiëren het logboek maar weigeren een levende proef te starten.', 'Les amis copient le journal, mais refusent de lancer un essai sur un être vivant.', 'The friends copy the log but refuse to start a test on a living being.', { tag:'living_test_refused', mood:'firm_boundary' }),
      b('Kroon activeert op afstand een tweede machine in een afgesloten kas.', 'Couronne active à distance une deuxième machine dans une serre fermée.', 'Crown remotely activates a second machine inside a locked greenhouse.', { tag:'greenhouse_activated', mood:'urgent_shift' }),
    ],
  },
  {
    titles: t('Een levende proef', 'Un essai vivant', 'A Living Test'),
    context: t('het herstel van de levende proef', 'la réparation après l’essai vivant', 'the recovery from the living trial'),
    location: 'greenhouse', characters: ['robbie','fien','mats','noor'],
    principle: t('toestemming en een veilige terugkeer zijn verplicht bij iedere levende proef', 'le consentement et un retour sûr sont obligatoires pour tout essai vivant', 'consent and a safe return are required for every living test'),
    moments: [
      b('In de afgesloten kas krimpt een bak jonge boompjes tot in een glazen schaal.', 'Dans la serre fermée, un bac de jeunes arbres rétrécit dans une coupe en verre.', 'Inside the locked greenhouse, a tray of young trees shrinks into a glass dish.', { tag:'saplings_shrunk', mood:'living_alarm' }),
      b('De verzorger zegt dat hij alleen toestemming gaf voor een gewone groeimeting.', 'Le jardinier dit avoir seulement accepté une mesure ordinaire de croissance.', 'The caretaker says he agreed only to an ordinary growth measurement.', { tag:'consent_exceeded', mood:'betrayed_consent' }),
      b('Robbie stopt de machine voordat insecten en vogels in het bereik komen.', 'Robbie arrête la machine avant que des insectes et des oiseaux entrent dans sa portée.', 'Robbie stops the machine before insects and birds enter its range.', { tag:'range_stopped', mood:'protective_action' }),
      b('De terugkeerknop vergroot de stammen, maar wortels en water blijven te klein.', 'Le bouton de retour agrandit les troncs, mais les racines et l’eau restent trop petites.', 'The return button enlarges the trunks, but the roots and water remain too small.', { tag:'return_fails', mood:'failed_reversal' }),
      b('Noor ontdekt dat Kroon de terugkeerproef nooit met een volledig levend systeem uitvoerde.', 'Nora découvre que Couronne n’a jamais testé le retour sur un système vivant complet.', 'Nora discovers Crown never tested the return on a complete living system.', { tag:'return_never_tested', mood:'grave_omission' }),
      b('De groep herstelt bodem, wortels en water; levende of menselijke proeven blijven verboden tot een volledige terugkeer onafhankelijk is bewezen.', 'Le groupe restaure le sol, les racines et l’eau ; tout essai vivant ou humain reste interdit jusqu’à la preuve indépendante d’un retour complet.', 'The group restores soil, roots, and water; living or human trials remain forbidden until a complete return is independently proven.', { tag:'saplings_restored', mood:'careful_repair' }),
      b('Kroon stuurt een beeld waarop een hele mijnvallei in zijn hand lijkt te passen.', 'Couronne envoie une image où toute une vallée minière semble tenir dans sa main.', 'Crown sends an image in which an entire mining valley seems to fit in his hand.', { tag:'valley_in_hand', mood:'larger_threat' }),
    ],
  },
  {
    titles: t('Alles past in zijn hand', 'Tout tient dans sa main', 'Everything Fits in His Hand'),
    context: t('de beoordeling van de mijnvallei', 'l’évaluation de la vallée minière', 'the assessment of the mining valley'),
    location: 'mine_valley', characters: ['robbie','fien','mats','aicha','kroon'],
    principle: t('bewoners, water en dieren mogen niet uit een ruimtemodel verdwijnen', 'les habitants, l’eau et les animaux ne doivent pas disparaître d’un modèle spatial', 'residents, water, and animals must not disappear from a space model'),
    moments: [
      b('Kroon plaatst zijn grootste krimpmachine boven de mijnvallei.', 'Couronne place sa plus grande machine de réduction au-dessus de la vallée minière.', 'Crown positions his largest shrinking machine above the mining valley.', { tag:'large_machine_over_valley', mood:'major_threat' }),
      b('Hij belooft de mijn klein op te bergen en het land meteen vrij te maken.', 'Il promet de ranger la mine en petit et de libérer immédiatement le terrain.', 'He promises to store the mine in miniature and free the land immediately.', { tag:'crown_promise', mood:'tempting_claim' }),
      b('Robbie ziet dat zijn model de ondergrondse waterlaag niet bevat.', 'Robbie voit que son modèle ne contient pas la nappe d’eau souterraine.', 'Robbie sees that his model does not include the underground water layer.', { tag:'water_layer_missing', mood:'critical_gap' }),
      b('Aïcha wijst op dassen, insecten en planten die door het veld trekken.', 'Aïcha montre les blaireaux, les insectes et les plantes qui traversent la zone.', 'Aisha points to badgers, insects, and plants that move through the area.', { tag:'wildlife_routes', mood:'living_system' }),
      b('Fien vindt een bewonerslijst die Kroon uit zijn voorstelling heeft verwijderd.', 'Fanny trouve une liste d’habitants que Couronne a retirée de sa présentation.', 'Faye finds a resident list that Crown removed from his presentation.', { tag:'residents_removed', mood:'excluded_people' }),
      b('De raad weigert het plan, maar Kroon start de buitenste ringen zonder toestemming.', 'Le conseil refuse le plan, mais Couronne démarre les anneaux extérieurs sans autorisation.', 'The council rejects the plan, but Crown starts the outer rings without permission.', { tag:'rings_started', mood:'unauthorized_activation' }),
      b('De grond trekt in strakke lijnen uiteen terwijl water naar de machine stroomt.', 'Le sol se sépare en lignes nettes tandis que l’eau coule vers la machine.', 'The ground pulls apart in sharp lines while water flows toward the machine.', { tag:'field_breaks', mood:'immediate_crisis' }),
    ],
  },
  {
    titles: t('Het veld breekt open', 'Le terrain se fend', 'The Field Breaks Open'),
    context: t('de noodactie op het breukveld', 'l’intervention d’urgence sur le terrain fissuré', 'the emergency action on the cracked field'),
    location: 'collapsing_field', characters: ['robbie','fien','mats','aicha','residents'],
    principle: t('bij direct gevaar beschermt de kleinste gezamenlijke ingreep eerst het leven', 'en cas de danger direct, la plus petite action collective protège d’abord la vie', 'in immediate danger, the smallest shared intervention protects life first'),
    moments: [
      b('Een scheur loopt van de machine naar de eerste huizen van de vallei.', 'Une fissure court de la machine vers les premières maisons de la vallée.', 'A crack runs from the machine toward the first homes in the valley.', { tag:'crack_toward_homes', mood:'urgent_danger' }),
      b('Robbie voorspelt dat een volledige uitschakeling de opgeslagen energie ineens zal lossen.', 'Robbie prévoit qu’un arrêt complet libérera d’un coup l’énergie stockée.', 'Robbie predicts that a full shutdown will release the stored energy at once.', { tag:'shutdown_risk', mood:'dangerous_choice' }),
      b('Fien leidt bewoners en dieren via de hoge weg uit het risicogebied.', 'Fanny guide les habitants et les animaux hors de la zone par la route haute.', 'Faye guides residents and animals out of the risk area along the high road.', { tag:'evacuation_high_road', mood:'calm_leadership' }),
      b('Mats koppelt drie ringen los terwijl Aïcha de waterstroom omleidt.', 'Mathis déconnecte trois anneaux pendant qu’Aïcha détourne le courant d’eau.', 'Max disconnects three rings while Aisha diverts the water flow.', { tag:'tasks_split', mood:'coordinated_action' }),
      b('Robbie verlaagt het veld in kleine stappen en laat elke stap door de groep bevestigen.', 'Robbie réduit le champ par petites étapes et fait confirmer chacune par le groupe.', 'Robbie lowers the field in small steps and has the group confirm each one.', { tag:'field_lowered_gradually', mood:'shared_control' }),
      b('De scheur stopt op enkele meters van de huizen en de waterlaag wordt weer stabiel.', 'La fissure s’arrête à quelques mètres des maisons et la nappe d’eau se stabilise.', 'The crack stops a few meters from the homes and the water layer stabilizes again.', { tag:'collapse_stopped', mood:'earned_relief' }),
      b('Tussen de beschadigde onderdelen ontbreekt Kroons draagbare kern met alle ontwerpen.', 'Parmi les pièces endommagées, le cœur portable de Couronne et tous les plans ont disparu.', 'Among the damaged parts, Crown’s portable core containing all the designs is missing.', { tag:'portable_core_missing', mood:'crown_escaped' }),
    ],
  },
  {
    titles: t('De gevaarlijke score', 'Le score dangereux', 'The Dangerous Score'),
    context: t('de openbare beoordeling van de gevaarlijke score', 'l’examen public du score dangereux', 'the public review of the dangerous score'),
    location: 'old_workshop_main_room', characters: ['robbie','fien','mats','noor','mayor'],
    principle: t('schade voorkomen mag menselijke vrijheid niet onzichtbaar als risico behandelen', 'prévenir les dégâts ne doit pas traiter invisiblement la liberté humaine comme un risque', 'preventing harm must not invisibly treat human freedom as a risk'),
    moments: [
      b('Terug in de Oude Werkplaats maakt Lotte alle bewijzen uit de mijn openbaar.', 'De retour au Vieil Atelier, Charlotte rend publiques toutes les preuves de la mine.', 'Back in the Old Workshop, Lottie makes all the evidence from the mine public.', { tag:'mine_evidence_public', mood:'public_accountability' }),
      b('Robbie toont waarom één schadescore de rivier, het bos en het moeras verkeerd beoordeelde.', 'Robbie montre pourquoi un seul score de dégâts jugeait mal la rivière, la forêt et le marais.', 'Robbie shows why one harm score misjudged the river, forest, and wetland.', { tag:'score_failures_shown', mood:'clear_lesson' }),
      b('Noor verwijdert Kroons vaste groeiregel en documenteert iedere nieuwe grens.', 'Nora retire la règle fixe de croissance de Couronne et documente chaque nouvelle limite.', 'Nora removes Crown’s fixed growth rule and documents every new boundary.', { tag:'fixed_rule_removed', mood:'careful_repair' }),
      b('Fien vraagt Robbie of zij na deze fouten nog één wereldoplossing wil berekenen.', 'Fanny demande à Robbie si elle veut encore calculer une seule solution mondiale après ces erreurs.', 'Faye asks Robbie whether she still wants to calculate one world solution after these mistakes.', { tag:'one_solution_questioned', mood:'honest_conversation' }),
      b('Robbie antwoordt dat meerdere controleerbare plannen beter zijn dan één gesloten bevel.', 'Robbie répond que plusieurs plans contrôlables valent mieux qu’un ordre unique et fermé.', 'Robbie answers that several reviewable plans are better than one closed command.', { tag:'many_reviewable_plans', mood:'mature_answer' }),
      b('Op haar privékaart bewaart Robbie toch een nieuwe score voor vrijheid, macht en menselijk risico.', 'Sur sa carte privée, Robbie conserve pourtant un nouveau score sur la liberté, le pouvoir et le risque humain.', 'On her private map, Robbie nevertheless keeps a new score for freedom, power, and human risk.', { tag:'private_freedom_score', mood:'quiet_unease' }),
      b('Ver weg activeert Kroon een verborgen synchronisatieroute in zijn draagbare kern en ontvangt hij delen van Robbies nieuwe privéberekening.', 'Au loin, Couronne active une route de synchronisation cachée dans son cœur portable et reçoit des fragments du nouveau calcul privé de Robbie.', 'Far away, Crown activates a hidden synchronization route in his portable core and receives fragments of Robbie’s new private calculation.', { location:'kroon_remote_lab', characters:['kroon'], tag:'crown_sees_new_score', mood:'future_threat' }),
    ],
  },
];

const chapters = specs.map((chapter, index) => ({
  ...chapter,
  range: ranges[index],
  beats: arc(index, chapter.moments, chapter.context),
}));

const result = writeBookSources(root, { slug:'l3_m5', bookId:'L3_M5', level:'M5', chapters });
console.log(JSON.stringify({ ...result, outputRoot:path.relative(root, result.outputRoot).split(path.sep).join('/') }, null, 2));
