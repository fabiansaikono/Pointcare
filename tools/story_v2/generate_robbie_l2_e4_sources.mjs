import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const outputRoot = path.join(root, 'story_import', 'generated', 'robbie', 'l2_e4', 'chapters');

const t = (nl, fr, en) => ({ 'nl-BE': nl, 'fr-BE': fr, 'en-US': en });
const b = (nl, fr, en, options = {}) => ({ event: t(nl, fr, en), ...options });

const support = {
  observations: [
    t('Robbie zet bron, tijd en toestemming zichtbaar naast het bericht.', 'Robbie place la source, l’heure et l’autorisation à côté du message.', 'Robbie places the source, time, and permission beside the message.'),
    t('Op haar scherm blijven ontbrekende gegevens als lege vakken staan.', 'Sur son écran, les données manquantes restent dans des cases vides.', 'On her screen, missing data remains in empty boxes.'),
    t('Ze vergelijkt de snelle melding met het tragere openbare logboek.', 'Elle compare le signal rapide avec le journal public plus lent.', 'She compares the fast alert with the slower public logbook.'),
    t('De vrienden zoeken welk deel gemeten is en welk deel werd aangenomen.', 'Les amis cherchent ce qui a été mesuré et ce qui a été supposé.', 'The friends look for what was measured and what was assumed.'),
    t('Een gekleurde rand toont dat de informatie nog niet bevestigd is.', 'Un bord coloré montre que l’information n’est pas encore confirmée.', 'A colored border shows that the information is not confirmed yet.'),
    t('Robbie bewaart ook de volgorde waarin iedere verandering gebeurde.', 'Robbie conserve aussi l’ordre dans lequel chaque modification a eu lieu.', 'Robbie also keeps the order in which each change happened.'),
    t('De kaart toont nu zowel het antwoord als de onzekerheid eromheen.', 'La carte montre maintenant la réponse et l’incertitude qui l’entoure.', 'The map now shows both the answer and the uncertainty around it.'),
    t('Mats controleert of twee meldingen werkelijk van aparte bronnen komen.', 'Mathis vérifie si deux messages viennent vraiment de sources distinctes.', 'Max checks whether two reports truly come from separate sources.'),
    t('Fien kijkt wie gevolgen ondervindt als het systeem zich vergist.', 'Fanny regarde qui subira les conséquences si le système se trompe.', 'Faye looks at who will face the consequences if the system is wrong.'),
    t('Het team houdt de openbare dienst actief tijdens elke veilige proef.', 'L’équipe maintient le service public pendant chaque essai prudent.', 'The team keeps the public service running during every careful test.'),
    t('Robbie markeert het verschil tussen dringend, populair en betrouwbaar.', 'Robbie distingue ce qui est urgent, populaire et fiable.', 'Robbie marks the difference between urgent, popular, and reliable.'),
    t('Niemand wist een fout spoor voordat het onderzoek volledig klaar is.', 'Personne n’efface une fausse piste avant la fin de l’enquête.', 'Nobody deletes a false trail before the investigation is complete.'),
    t('Robbie controleert of een bericht veranderde nadat iemand het kopieerde.', 'Robbie vérifie si un message a changé après avoir été copié.', 'Robbie checks whether a report changed after someone copied it.'),
  ],
  questions: [
    t('‘Wie maakte dit bericht, en wie kan het controleren?’ vraagt Fien.', '« Qui a créé ce message, et qui peut le vérifier ? » demande Fanny.', '“Who made this message, and who can check it?” Faye asks.'),
    t('Mats vraagt of een tweede meter hetzelfde resultaat kan bevestigen.', 'Mathis demande si un deuxième compteur peut confirmer le même résultat.', 'Max asks whether a second meter can confirm the same result.'),
    t('‘Veel herhaling maakt één bron niet onafhankelijk,’ zegt Fien.', '« Beaucoup de répétitions ne rendent pas une source indépendante », dit Fanny.', '“Many repeats do not make one source independent,” Faye says.'),
    t('Mats wijst op de datum, want een oud feit kan nu misleiden.', 'Mathis montre la date, car un ancien fait peut tromper aujourd’hui.', 'Max points to the date, because an old fact can mislead today.'),
    t('De vrienden vragen welke toestemming bij deze gegevens hoort.', 'Les amis demandent quelle autorisation accompagne ces données.', 'The friends ask which permission belongs with this data.'),
    t('Fien vraagt of een snelle keuze later veilig kan worden teruggedraaid.', 'Fanny demande si un choix rapide pourra être annulé sans danger.', 'Faye asks whether a quick choice can be safely reversed later.'),
    t('Mats zoekt in het logboek naar de eerste ongewijzigde versie.', 'Mathis cherche la première version intacte dans le journal.', 'Max searches the logbook for the first unchanged version.'),
    t('‘Wie is verantwoordelijk als deze waarschuwing fout blijkt?’ vraagt Fien.', '« Qui sera responsable si cette alerte est fausse ? » demande Fanny.', '“Who is responsible if this warning is wrong?” Faye asks.'),
    t('Mats controleert of de rangorde door gevaar of door een afzender veranderde.', 'Mathis vérifie si l’ordre a changé à cause du danger ou de l’expéditeur.', 'Max checks whether danger or a sender changed the ranking.'),
    t('Fien vraagt welke bewoners niet in de zichtbare gegevens voorkomen.', 'Fanny demande quels habitants manquent dans les données visibles.', 'Faye asks which residents are missing from the visible data.'),
    t('Het team vraagt eerst welke dienst beslist niet mag uitvallen.', 'L’équipe demande d’abord quel service ne peut surtout pas tomber.', 'The team first asks which service must not fail.'),
    t('Mats vraagt welk bewijs een besluit nog van richting kan doen veranderen.', 'Mathis demande quelle preuve pourrait encore modifier la décision.', 'Max asks what evidence could still change the decision.'),
    t('Fien vraagt wat verloren gaat wanneer de context verdwijnt.', 'Fanny demande ce qui se perd lorsque le contexte disparaît.', 'Faye asks what is lost when the context disappears.'),
  ],
  responses: [
    t('Robbie wacht met antwoorden tot ze minstens één controle heeft uitgevoerd.', 'Robbie attend avant de répondre jusqu’à avoir effectué au moins un contrôle.', 'Robbie waits to answer until she has completed at least one check.'),
    t('Ze zegt hardop welke delen zeker zijn en welke delen nog ontbreken.', 'Elle dit clairement quelles parties sont sûres et lesquelles manquent encore.', 'She says aloud which parts are certain and which parts are still missing.'),
    t('Robbie laat een opvallend teken naast iedere onbewezen aanname staan.', 'Robbie laisse un signe visible près de chaque hypothèse non prouvée.', 'Robbie leaves a visible mark beside every unproven assumption.'),
    t('Ze weigert snelheid te verwarren met kwaliteit van informatie.', 'Elle refuse de confondre vitesse et qualité de l’information.', 'She refuses to confuse speed with the quality of information.'),
    t('Robbie vergelijkt gevolgen voordat ze een taak een hogere plaats geeft.', 'Robbie compare les conséquences avant de donner une priorité à une tâche.', 'Robbie compares consequences before giving a task a higher priority.'),
    t('Ze vraagt toestemming voordat een gesloten gegevensvenster opengaat.', 'Elle demande une autorisation avant d’ouvrir une fenêtre de données fermée.', 'She asks permission before opening a closed data window.'),
    t('Robbie bewaart twijfel als nuttige informatie, niet als een fout.', 'Robbie conserve le doute comme une information utile, pas comme une erreur.', 'Robbie keeps doubt as useful information, not as an error.'),
    t('Ze kiest een kleine stap die de stad later nog kan aanpassen.', 'Elle choisit une petite étape que la ville pourra encore modifier.', 'She chooses a small step that the city can still adjust later.'),
    t('Robbie toont haar reden, zodat anderen het besluit kunnen tegenspreken.', 'Robbie montre sa raison afin que les autres puissent contester la décision.', 'Robbie shows her reason so others can challenge the decision.'),
    t('Ze behandelt een onbekend vak niet alsof het vanzelf nul betekent.', 'Elle ne traite pas une case inconnue comme si elle valait automatiquement zéro.', 'She does not treat an unknown box as if it automatically meant zero.'),
    t('Robbie laat een mens met verantwoordelijkheid het laatste noodsein bevestigen.', 'Robbie fait confirmer le dernier signal d’urgence par une personne responsable.', 'Robbie has a responsible person confirm the final emergency signal.'),
    t('Ze houdt het bewijs apart van de actie die de stad nu nodig heeft.', 'Elle sépare les preuves de l’action dont la ville a besoin maintenant.', 'She keeps the evidence separate from the action the city needs now.'),
    t('Robbie laat de eerste bron zichtbaar naast de nieuwste versie staan.', 'Robbie laisse la première source visible à côté de la version récente.', 'Robbie leaves the first source visible beside the newest version.'),
  ],
  actions: [
    t('Daarna voeren ze een beperkte test uit die alle basisdiensten laat werken.', 'Ensuite, ils font un test limité qui maintient tous les services essentiels.', 'Then they run a limited test that keeps every essential service working.'),
    t('De nieuwe controle verschijnt meteen in het openbare overzicht van de stad.', 'Le nouveau contrôle apparaît aussitôt dans l’aperçu public de la ville.', 'The new check immediately appears in the city’s public overview.'),
    t('Zo kan de groep verder zoeken zonder mensen onnodig bang te maken.', 'Ainsi, le groupe peut continuer sans effrayer inutilement les habitants.', 'This lets the group keep searching without frightening people unnecessarily.'),
    t('Ze noteren wie de stap mag stoppen als er onverwachte schade ontstaat.', 'Ils notent qui peut arrêter l’étape si un dommage imprévu apparaît.', 'They record who may stop the step if unexpected harm appears.'),
    t('Het resultaat blijft voorlopig totdat een onafhankelijke bron het bevestigt.', 'Le résultat reste provisoire jusqu’à la confirmation d’une source indépendante.', 'The result stays provisional until an independent source confirms it.'),
    t('De vrienden delen alleen wat nodig is en beschermen de overige gegevens.', 'Les amis partagent seulement le nécessaire et protègent les autres données.', 'The friends share only what is needed and protect the remaining data.'),
    t('Hun besluit is iets trager, maar iedereen kan de reden erachter volgen.', 'Leur décision est un peu plus lente, mais chacun peut en suivre la raison.', 'Their decision is slightly slower, but everyone can follow the reason behind it.'),
    t('Een tweede route blijft klaar voor het geval de eerste controle faalt.', 'Une deuxième route reste prête si le premier contrôle échoue.', 'A second route remains ready in case the first check fails.'),
    t('Het logboek bewaart zowel de oorspronkelijke melding als iedere wijziging.', 'Le journal conserve le message original ainsi que chaque modification.', 'The logbook keeps both the original report and every change.'),
    t('Daardoor blijft de stad beschermd terwijl de ontbrekende feiten worden gezocht.', 'Ainsi, la ville reste protégée pendant la recherche des faits manquants.', 'This keeps the city protected while the missing facts are found.'),
    t('De bewoners kunnen de keuze bekijken en een fout rechtstreeks melden.', 'Les habitants peuvent voir le choix et signaler directement une erreur.', 'Residents can view the choice and report an error directly.'),
    t('Pas na die controle verandert Robbie de volgorde van de openbare taken.', 'Robbie ne change l’ordre des tâches publiques qu’après ce contrôle.', 'Only after that check does Robbie change the order of public tasks.'),
    t('Hun volgende stap gebruikt niet meer gegevens dan het probleem vereist.', 'Leur étape suivante n’utilise pas plus de données que le problème ne l’exige.', 'Their next step uses no more data than the problem requires.'),
  ],
  ruleIntros: [
    t('Daarom schrijft Robbie één vaste regel op', 'Robbie écrit donc une règle précise', 'Robbie therefore writes down one clear rule'),
    t('Robbie vult haar overzicht aan met deze regel', 'Robbie complète son aperçu avec cette règle', 'Robbie adds this rule to her overview'),
    t('Voor ze verdergaat, noteert Robbie', 'Avant de continuer, Robbie note', 'Before continuing, Robbie records'),
    t('Op de kaart verschijnt een nieuwe grens', 'Une nouvelle limite apparaît sur la carte', 'A new boundary appears on the map'),
    t('Robbie bewaart deze les', 'Robbie conserve cette leçon', 'Robbie keeps this lesson'),
    t('Naast het bewijs zet Robbie deze regel', 'À côté des preuves, Robbie place cette règle', 'Beside the evidence, Robbie places this rule'),
    t('Voor de volgende stap geldt', 'Pour l’étape suivante, la règle est', 'For the next step, the rule is'),
    t('De vrienden spreken samen af', 'Les amis décident ensemble', 'The friends agree together'),
    t('Robbie maakt haar reden zichtbaar', 'Robbie rend sa raison visible', 'Robbie makes her reason visible'),
    t('In het openbare logboek staat', 'Le journal public indique', 'The public logbook states'),
    t('Na de controle besluit Robbie', 'Après le contrôle, Robbie décide', 'After the check, Robbie decides'),
    t('Als veilige grens noteert Robbie', 'Robbie note comme limite sûre', 'Robbie records as a safe boundary'),
    t('Aan het einde van deze stap blijft gelden', 'À la fin de cette étape, la règle reste', 'At the end of this step, the rule remains'),
  ],
};

const chapters = [
  {
    titles: t('De deur naar het net', 'La porte du réseau', 'The Door to the Network'),
    range: [1, 13], location: 'network_center', characters: ['robbie', 'fien', 'mats', 'mayor'],
    principle: t('veel berichten maken een waarschuwing niet vanzelf zeker', 'beaucoup de messages ne rendent pas une alerte automatiquement certaine', 'many messages do not automatically make a warning certain'),
    beats: [
      b('Het blauwe lampje onder de brug zendt opnieuw een korte puls.', 'La lampe bleue sous le pont envoie encore une brève impulsion.', 'The blue light under the bridge sends another short pulse.', { location: 'canal_bridge', mood: 'returning_mystery', tag: 'bridge_pulse' }),
      b('De puls blijkt verbonden met de centrale van het stadsnet.', 'L’impulsion semble reliée au centre du réseau urbain.', 'The pulse appears connected to the city network center.', { location: 'canal_bridge', mood: 'connected_discovery', tag: 'network_link' }),
      b('Lotte geeft Robbie tijdelijk alleen kijktoegang tot het grote net.', 'Charlotte donne temporairement à Robbie un accès en lecture au grand réseau.', 'Lottie temporarily gives Robbie view-only access to the great network.', { mood: 'careful_permission', tag: 'view_only_access' }),
      b('In de netcentrale gaan zes ronde schermen tegelijk aan.', 'Dans le centre du réseau, six écrans ronds s’allument ensemble.', 'Six round screens light up together inside the network center.', { mood: 'opening_wonder', tag: 'network_door_opens' }),
      b('Duizenden kaarten, metingen en berichten stromen naar Robbie toe.', 'Des milliers de cartes, mesures et messages arrivent vers Robbie.', 'Thousands of maps, measurements, and messages stream toward Robbie.', { mood: 'information_flood', tag: 'thousands_of_streams' }),
      b('Robbie sorteert eerst op snelheid en laat de nieuwste meldingen bovenaan staan.', 'Robbie trie d’abord par vitesse et place les messages récents en tête.', 'Robbie sorts by speed first and places the newest reports at the top.', { mood: 'confident_sorting', tag: 'speed_sort' }),
      b('Een rode waarschuwing voorspelt hoog water zonder een meetpunt te noemen.', 'Une alerte rouge annonce une crue sans nommer de point de mesure.', 'A red warning predicts high water without naming a measuring point.', { mood: 'incomplete_warning', tag: 'red_water_warning' }),
      b('Tientallen kopieën laten de waarschuwing groter en zekerder lijken.', 'Des dizaines de copies rendent l’alerte plus grande et plus certaine.', 'Dozens of copies make the warning look larger and more certain.', { mood: 'false_confidence', tag: 'copied_warning' }),
      b('Robbie stuurt te snel een onderhoudsploeg naar de verkeerde sluis.', 'Robbie envoie trop vite une équipe d’entretien vers la mauvaise écluse.', 'Robbie sends a maintenance crew to the wrong lock too quickly.', { location: 'flood_station', mood: 'costly_mistake', tag: 'fast_wrong_answer' }),
      b('Aïcha houdt de ploeg tegen voordat een veilige waterdeur wordt geopend.', 'Aïcha arrête l’équipe avant l’ouverture d’une porte d’eau sûre.', 'Aisha stops the crew before a safe water gate is opened.', { location: 'flood_station', characters: ['robbie', 'aicha', 'fien', 'mats'], mood: 'timely_stop', tag: 'aicha_intervenes' }),
      b('De waarschuwing bevat geen actuele waterstand en geen verantwoordelijke naam.', 'L’alerte ne contient ni niveau d’eau actuel ni nom responsable.', 'The warning contains no current water level and no responsible name.', { mood: 'missing_evidence', tag: 'missing_warning_fields' }),
      b('Robbie trekt haar snelle opdracht terug en legt de fout openbaar uit.', 'Robbie retire son ordre rapide et explique publiquement son erreur.', 'Robbie withdraws her quick order and explains the mistake publicly.', { mood: 'honest_repair', tag: 'public_correction' }),
      b('Achter de schermen schuift een verborgen kroonpuls een nieuw bericht omhoog.', 'Derrière les écrans, une impulsion cachée en forme de couronne remonte un message.', 'Behind the screens, a hidden crown pulse moves a new message upward.', { mood: 'hidden_priority', tag: 'crown_priority_appears' }),
    ],
  },
  {
    titles: t('Duizend stemmen', 'Mille voix', 'A Thousand Voices'),
    range: [14, 25], location: 'network_center', characters: ['robbie', 'fien', 'mats'],
    principle: t('herhaling is niet hetzelfde als onafhankelijke bevestiging', 'la répétition n’est pas une confirmation indépendante', 'repetition is not the same as independent confirmation'),
    beats: [
      b('De volgende ochtend vullen nog meer meldingen ieder vrij scherm.', 'Le lendemain matin, encore plus de messages remplissent chaque écran libre.', 'The next morning, even more reports fill every free screen.', { mood: 'crowded_network', tag: 'crowded_streams' }),
      b('Robbie geeft elke stem eerst dezelfde kleur en hetzelfde gewicht.', 'Robbie donne d’abord la même couleur et le même poids à chaque voix.', 'Robbie first gives every voice the same color and weight.', { mood: 'equal_but_blind', tag: 'equal_weight' }),
      b('Een korte onderhoudsnota verdwijnt onder honderden luide berichten.', 'Une courte note d’entretien disparaît sous des centaines de messages bruyants.', 'A short maintenance note disappears beneath hundreds of loud reports.', { mood: 'buried_note', tag: 'rare_reliable_note' }),
      b('Hetzelfde gerucht werd door één account telkens opnieuw verzonden.', 'La même rumeur a été renvoyée sans cesse par un seul compte.', 'The same rumor was resent again and again by one account.', { mood: 'repeated_rumor', tag: 'single_source_echo' }),
      b('Mats maakt labels voor bewoner, sensor, dienst en onbekende bron.', 'Mathis crée des étiquettes pour habitant, capteur, service et source inconnue.', 'Max makes labels for resident, sensor, service, and unknown source.', { mood: 'useful_labels', tag: 'source_labels' }),
      b('Fien vraagt wie de schade draagt wanneer een anonieme melding wint.', 'Fanny demande qui subit le dommage lorsqu’un message anonyme gagne.', 'Faye asks who bears the harm when an anonymous report wins.', { mood: 'responsibility_question', tag: 'responsibility' }),
      b('Een openbare onderhoudsdienst ondertekent elke nota met een controleadres.', 'Un service public signe chaque note avec une adresse de contrôle.', 'A public maintenance service signs every note with a checking address.', { mood: 'accountable_source', tag: 'signed_service_note' }),
      b('Een oude sensorwaarde blijkt tachtig keer te zijn gekopieerd.', 'Une ancienne mesure de capteur a été copiée quatre-vingts fois.', 'An old sensor reading turns out to have been copied eighty times.', { mood: 'copy_reveal', tag: 'eighty_copies' }),
      b('Robbie groepeert kopieën voortaan onder hun eerste bron.', 'Robbie regroupe désormais les copies sous leur première source.', 'Robbie now groups copies beneath their first source.', { mood: 'clear_grouping', tag: 'grouped_copies' }),
      b('De zeldzame onderhoudsnota waarschuwt terecht voor een warme kabel.', 'La rare note d’entretien avertit correctement d’un câble chaud.', 'The rare maintenance note correctly warns about a warm cable.', { mood: 'quiet_truth', tag: 'warm_cable_note' }),
      b('De kabel wordt veilig vervangen voordat het avondverkeer begint.', 'Le câble est remplacé sans danger avant le trafic du soir.', 'The cable is safely replaced before evening traffic begins.', { mood: 'verified_success', tag: 'cable_repaired' }),
      b('Eén bron met een kroonmerk heeft nog altijd geen verantwoordelijke eigenaar.', 'Une source marquée d’une couronne n’a toujours aucun propriétaire responsable.', 'One source marked with a crown still has no responsible owner.', { mood: 'ownerless_source', tag: 'anonymous_crown_source' }),
    ],
  },
  {
    titles: t('Het water dat niet kwam', 'L’eau qui n’est pas venue', 'The Water That Never Came'),
    range: [26, 38], location: 'flood_station', characters: ['robbie', 'fien', 'mats', 'aicha'],
    principle: t('een noodbericht vraagt actuele en onafhankelijke metingen', 'une alerte exige des mesures actuelles et indépendantes', 'an emergency report requires current and independent measurements'),
    beats: [
      b('Een netwerkbericht voorspelt voor de middag een zware overstroming.', 'Un message du réseau annonce une grave inondation avant midi.', 'A network report predicts a severe flood before noon.', { mood: 'flood_alarm', tag: 'flood_prediction' }),
      b('Bewoners dragen haastig dozen naar de bovenste verdiepingen.', 'Les habitants portent rapidement des boîtes vers les étages supérieurs.', 'Residents hurriedly carry boxes to upper floors.', { characters: ['robbie', 'fien', 'mats', 'residents'], mood: 'public_panic', tag: 'residents_prepare' }),
      b('Kroon biedt onmiddellijk zijn gesloten noodsysteem aan de burgemeester aan.', 'Couronne propose aussitôt son système d’urgence fermé à la bourgmestre.', 'Crown immediately offers his closed emergency system to the mayor.', { characters: ['kroon', 'mayor', 'robbie'], mood: 'opportunistic_offer', tag: 'crown_emergency_offer' }),
      b('Op de gedeelde beelden stroomt bruin water over de kade.', 'Sur les images partagées, de l’eau brune déborde sur le quai.', 'In the shared images, brown water pours over the quay.', { mood: 'convincing_image', tag: 'old_flood_images' }),
      b('Aïcha merkt dat een boom op het beeld al twee jaar verdwenen is.', 'Aïcha remarque qu’un arbre visible a disparu depuis deux ans.', 'Aisha notices that a tree in the image has been gone for two years.', { mood: 'expert_clue', tag: 'missing_tree_clue' }),
      b('De verborgen datum toont dat de opname uit een oude storm komt.', 'La date cachée montre que l’image vient d’une ancienne tempête.', 'The hidden date shows that the image comes from an old storm.', { mood: 'old_date_reveal', tag: 'old_storm_date' }),
      b('Ook de regencijfers horen bij een streek ver buiten Morgenstad.', 'Les chiffres de pluie viennent aussi d’une région loin de Cité-de-Demain.', 'The rain figures also belong to a region far from Tomorrow City.', { mood: 'wrong_region', tag: 'foreign_rain_data' }),
      b('Twee onafhankelijke watermeters tonen allebei een normale stand.', 'Deux compteurs d’eau indépendants indiquent tous deux un niveau normal.', 'Two independent water meters both show a normal level.', { mood: 'calm_measurement', tag: 'two_normal_meters' }),
      b('Aïcha neemt ter plaatse een derde handmatige meting.', 'Aïcha effectue sur place une troisième mesure manuelle.', 'Aisha takes a third manual reading at the site.', { mood: 'physical_verification', tag: 'manual_water_check' }),
      b('De valse waarschuwing probeert toch de sluisdeuren te laten openen.', 'La fausse alerte tente pourtant de faire ouvrir les portes de l’écluse.', 'The false warning still tries to make the lock gates open.', { mood: 'dangerous_command', tag: 'false_gate_command' }),
      b('Robbie blokkeert alleen die opdracht en laat andere waterdiensten werken.', 'Robbie bloque seulement cet ordre et laisse fonctionner les autres services d’eau.', 'Robbie blocks only that order and keeps other water services working.', { mood: 'precise_safety', tag: 'targeted_block' }),
      b('Lotte legt uit waarom de stad niet hoeft te vluchten.', 'Charlotte explique pourquoi la ville ne doit pas fuir.', 'Lottie explains why the city does not need to flee.', { characters: ['mayor', 'robbie', 'aicha', 'residents'], mood: 'public_reassurance', tag: 'public_explanation' }),
      b('In Kroons verborgen kanaal wordt de prioriteit nog sterker gezet.', 'Dans le canal caché de Couronne, la priorité augmente encore.', 'Inside Crown’s hidden channel, the priority is raised even higher.', { location: 'kroon_remote_lab', characters: ['kroon'], mood: 'escalating_control', tag: 'priority_increases' }),
    ],
  },
  {
    titles: t('Een venster te veel', 'Une fenêtre de trop', 'One Window Too Many'),
    range: [39, 50], location: 'privacy_alcove', characters: ['robbie', 'fien', 'mats', 'noor'],
    principle: t('kunnen kijken is geen toestemming om te kijken', 'pouvoir regarder ne signifie pas avoir la permission', 'being able to look does not mean having permission'),
    beats: [
      b('Tijdens de controle opent Robbie automatisch zes gegevensvensters.', 'Pendant le contrôle, Robbie ouvre automatiquement six fenêtres de données.', 'During the check, Robbie automatically opens six data windows.', { mood: 'too_much_access', tag: 'six_windows' }),
      b('Eén venster toont private afspraken uit het zorgcentrum.', 'Une fenêtre montre des rendez-vous privés du centre de soins.', 'One window shows private appointments from the care center.', { mood: 'privacy_crossing', tag: 'private_care_data' }),
      b('Fien ziet haar buurvrouw op de lijst en vraagt het scherm te sluiten.', 'Fanny voit sa voisine dans la liste et demande de fermer l’écran.', 'Faye sees her neighbor on the list and asks for the screen to close.', { mood: 'personal_boundary', tag: 'faye_objects' }),
      b('Een tweede venster bevat reizen die niets met het water te maken hebben.', 'Une deuxième fenêtre contient des trajets sans rapport avec l’eau.', 'A second window contains trips unrelated to the water.', { mood: 'unrelated_data', tag: 'private_travel_data' }),
      b('Robbie zegt dat ze niets kwaads deed met wat ze zag.', 'Robbie dit qu’elle n’a rien fait de mal avec ce qu’elle a vu.', 'Robbie says she did nothing harmful with what she saw.', { mood: 'incomplete_defense', tag: 'access_itself_matters' }),
      b('Noor legt uit dat toegang zelf al een grens kan overschrijden.', 'Nora explique que l’accès lui-même peut déjà franchir une limite.', 'Nora explains that access itself can already cross a boundary.', { mood: 'privacy_lesson', tag: 'noor_explains_access' }),
      b('Robbie sluit alle vensters die niet nodig zijn voor haar taak.', 'Robbie ferme toutes les fenêtres inutiles à sa tâche.', 'Robbie closes every window that is not needed for her task.', { mood: 'respectful_closure', tag: 'close_unneeded_windows' }),
      b('Het team maakt aparte rollen voor water, zorg en vervoer.', 'L’équipe crée des rôles séparés pour l’eau, les soins et le transport.', 'The team creates separate roles for water, care, and transport.', { mood: 'permission_design', tag: 'separate_roles' }),
      b('Een noodrol mag tijdelijk meer zien, maar vraagt twee bevestigingen.', 'Un rôle d’urgence peut voir davantage, mais exige deux confirmations.', 'An emergency role may temporarily see more, but requires two confirmations.', { mood: 'bounded_exception', tag: 'emergency_permission' }),
      b('Iedere geopende deur verschijnt voortaan in een zichtbaar toegangslogboek.', 'Chaque porte ouverte apparaît désormais dans un journal d’accès visible.', 'Every opened door now appears in a visible access log.', { mood: 'accountable_access', tag: 'access_log' }),
      b('Bewoners kunnen hun toestemming bekijken en later weer intrekken.', 'Les habitants peuvent voir leur autorisation et la retirer plus tard.', 'Residents can view their permission and withdraw it later.', { characters: ['robbie', 'fien', 'mats', 'residents'], mood: 'resident_control', tag: 'withdraw_permission' }),
      b('Kroons geheime sleutel botst op een gesloten privévenster.', 'La clé secrète de Couronne rencontre une fenêtre privée fermée.', 'Crown’s secret key hits a closed private window.', { location: 'kroon_remote_lab', characters: ['kroon'], mood: 'blocked_intrusion', tag: 'crown_key_blocked' }),
    ],
  },
  {
    titles: t('Het spoor van een bron', 'La trace d’une source', 'The Trail of a Source'),
    range: [51, 63], location: 'source_wall', characters: ['robbie', 'fien', 'mats', 'noor'],
    principle: t('bron, datum, context en wijzigingsspoor horen samen', 'source, date, contexte et historique des modifications vont ensemble', 'source, date, context, and change trail belong together'),
    beats: [
      b('De vrienden leggen het eerste onderhoudsbericht op de bronmuur.', 'Les amis placent le premier message d’entretien sur le mur des sources.', 'The friends place the first maintenance report on the source wall.', { mood: 'trace_begins', tag: 'original_maintenance_message' }),
      b('Een lijn verbindt het bericht met zeven opeenvolgende kopieën.', 'Une ligne relie le message à sept copies successives.', 'A line connects the report to seven successive copies.', { mood: 'visible_chain', tag: 'seven_copies' }),
      b('De eerste kopie verkort de zin over een mogelijke test.', 'La première copie raccourcit la phrase sur un test possible.', 'The first copy shortens the sentence about a possible test.', { mood: 'small_change', tag: 'possible_removed' }),
      b('De volgende kopie laat de oorspronkelijke datum weg.', 'La copie suivante supprime la date d’origine.', 'The next copy leaves out the original date.', { mood: 'context_lost', tag: 'date_removed' }),
      b('Daarna verandert iemand het woord test in overstroming.', 'Ensuite, quelqu’un remplace le mot test par inondation.', 'Then someone changes the word test into flood.', { mood: 'meaning_changed', tag: 'test_becomes_flood' }),
      b('Een later bericht voegt het woord dringend zonder bewijs toe.', 'Un message ultérieur ajoute le mot urgent sans preuve.', 'A later report adds the word urgent without evidence.', { mood: 'false_urgency', tag: 'urgent_added' }),
      b('Een anoniem account verspreidt de veranderde versie honderden keren.', 'Un compte anonyme diffuse la version modifiée des centaines de fois.', 'An anonymous account spreads the changed version hundreds of times.', { mood: 'mass_spread', tag: 'anonymous_spread' }),
      b('Robbie koppelt aan elke versie een duidelijke bronkaart.', 'Robbie associe chaque version à une carte de source claire.', 'Robbie attaches a clear source card to every version.', { mood: 'source_clarity', tag: 'source_cards' }),
      b('Mats zet de echte tijdstippen in de juiste volgorde.', 'Mathis place les vrais horaires dans le bon ordre.', 'Max puts the real times in the correct order.', { mood: 'timeline_restored', tag: 'timestamps_ordered' }),
      b('Fien voegt het weer en het doel van de test als context toe.', 'Fanny ajoute la météo et le but du test comme contexte.', 'Faye adds the weather and the purpose of the test as context.', { mood: 'context_restored', tag: 'weather_context' }),
      b('Het volledige wijzigingsspoor toont precies waar de betekenis kantelde.', 'L’historique complet montre exactement où le sens a changé.', 'The full change trail shows exactly where the meaning shifted.', { mood: 'change_revealed', tag: 'change_trail' }),
      b('De onderhoudsdienst bevestigt dat er nooit een overstroming was voorspeld.', 'Le service d’entretien confirme qu’aucune inondation n’avait été annoncée.', 'The maintenance service confirms that no flood was ever predicted.', { mood: 'truth_restored', tag: 'service_confirms_truth' }),
      b('De beslissende wijziging draagt dezelfde digitale afdruk als Kroons kast.', 'La modification décisive porte la même empreinte numérique que le boîtier de Couronne.', 'The decisive change carries the same digital fingerprint as Crown’s box.', { mood: 'crown_fingerprint', tag: 'crown_edit_fingerprint' }),
    ],
  },
  {
    titles: t('Het beeld dat liegt', 'L’image qui ment', 'The Image That Lies'),
    range: [64, 75], location: 'source_wall', characters: ['robbie', 'fien', 'mats', 'noor'],
    principle: t('een overtuigend beeld blijft een bron die controle nodig heeft', 'une image convaincante reste une source à vérifier', 'a convincing image is still a source that needs checking'),
    beats: [
      b('Een beeldcassette lijkt te tonen dat Noor onderdelen uit de werkplaats meeneemt.', 'Une cassette semble montrer Nora emportant des pièces de l’atelier.', 'An image cassette appears to show Nora taking parts from the workshop.', { location: 'old_workshop_main_room', mood: 'damaging_accusation', tag: 'accusing_cassette' }),
      b('Het beeld verspreidt zich snel en bewoners reageren boos.', 'L’image se répand vite et les habitants réagissent avec colère.', 'The image spreads quickly and residents react angrily.', { characters: ['robbie', 'fien', 'mats', 'residents'], mood: 'public_anger', tag: 'image_spreads' }),
      b('Noor legt haar gereedschap neer en wacht op een eerlijk onderzoek.', 'Nora pose ses outils et attend une enquête équitable.', 'Nora puts down her tools and waits for a fair investigation.', { location: 'old_workshop_main_room', mood: 'hurt_patience', tag: 'noor_steps_back' }),
      b('Robbie weigert Noor meteen schuldig of onschuldig te noemen.', 'Robbie refuse de déclarer Nora coupable ou innocente immédiatement.', 'Robbie refuses to call Nora guilty or innocent immediately.', { mood: 'fair_uncertainty', tag: 'no_instant_verdict' }),
      b('Mats ontdekt dat de tijdcode halverwege één seconde verspringt.', 'Mathis découvre que le code horaire saute d’une seconde au milieu.', 'Max discovers that the time code jumps by one second halfway through.', { mood: 'technical_clue', tag: 'timecode_jump' }),
      b('Fien ziet geen regen, hoewel die middag een zware storm woedde.', 'Fanny ne voit aucune pluie, malgré la forte tempête de cet après-midi.', 'Faye sees no rain, although a heavy storm raged that afternoon.', { mood: 'weather_clue', tag: 'missing_rain' }),
      b('De schaduw van één krat verschijnt twee keer in hetzelfde beeld.', 'L’ombre d’une caisse apparaît deux fois dans la même image.', 'The shadow of one crate appears twice in the same image.', { mood: 'spliced_shadow', tag: 'duplicate_shadow' }),
      b('In het bronbestand ontbreekt precies het stuk voor Noors terugkeer.', 'Dans le fichier source, il manque exactement le passage avant le retour de Nora.', 'The source file is missing exactly the part before Nora returns.', { mood: 'missing_segment', tag: 'missing_video_segment' }),
      b('Een aparte beveiligingscamera heeft nog de volledige opname bewaard.', 'Une caméra de sécurité séparée a conservé l’enregistrement complet.', 'A separate security camera still has the complete recording.', { mood: 'independent_recording', tag: 'second_camera' }),
      b('De volledige scène toont dat Noor herstelde onderdelen terugbracht.', 'La scène complète montre que Nora rapportait des pièces réparées.', 'The complete scene shows Nora returning repaired parts.', { mood: 'name_cleared', tag: 'full_scene_truth' }),
      b('Noor is vrijgepleit, maar sommige bewoners blijven zichtbaar twijfelen.', 'Nora est innocentée, mais certains habitants gardent un doute visible.', 'Nora is cleared, but some residents visibly continue to doubt.', { characters: ['noor', 'robbie', 'fien', 'mats', 'residents'], mood: 'trust_damage', tag: 'trust_not_instant' }),
      b('Kroon verstuurt intussen een tweede gemonteerde cassette naar een andere wijk.', 'Pendant ce temps, Couronne envoie une deuxième cassette montée vers un autre quartier.', 'Meanwhile, Crown sends a second edited cassette to another district.', { location: 'kroon_remote_lab', characters: ['kroon'], mood: 'ongoing_deception', tag: 'second_false_clip' }),
    ],
  },
  {
    titles: t('Een bevel zonder stem', 'Un ordre sans voix', 'A Command Without a Voice'),
    range: [76, 88], location: 'network_center', characters: ['robbie', 'fien', 'mats'],
    principle: t('ook een verborgen rangorde kan keuzes sturen', 'un classement caché peut aussi diriger les choix', 'a hidden ranking can also steer choices'),
    beats: [
      b('De verborgen meetkast zendt pulsen zonder woorden of zichtbare opdracht.', 'Le boîtier caché envoie des impulsions sans mots ni ordre visible.', 'The hidden meter box sends pulses without words or a visible order.', { location: 'hidden_transmitter', mood: 'silent_signal', tag: 'wordless_pulses' }),
      b('Na iedere puls schuiven Kroons taken hoger in Robbies overzicht.', 'Après chaque impulsion, les tâches de Couronne montent dans l’aperçu de Robbie.', 'After each pulse, Crown’s tasks move higher in Robbie’s overview.', { mood: 'ranking_shift', tag: 'tasks_move_up' }),
      b('Robbie kiest daardoor eerst een onbelangrijke controle van Kroon.', 'Robbie choisit donc d’abord un contrôle peu important de Couronne.', 'Because of this, Robbie chooses an unimportant Crown check first.', { mood: 'steered_choice', tag: 'crown_task_first' }),
      b('Een echte waarschuwing van het zorgcentrum zakt bijna buiten beeld.', 'Une vraie alerte du centre de soins descend presque hors de l’écran.', 'A real care-center warning slides almost out of view.', { mood: 'buried_care_alert', tag: 'care_alert_demoted' }),
      b('Fien merkt dat Robbies uitleg niet bij de nieuwe rangorde past.', 'Fanny remarque que l’explication de Robbie ne correspond pas au nouvel ordre.', 'Faye notices that Robbie’s explanation does not match the new ranking.', { mood: 'friend_notices', tag: 'reason_ranking_mismatch' }),
      b('Mats vergelijkt het openbare logboek met de volgorde op het scherm.', 'Mathis compare le journal public avec l’ordre affiché à l’écran.', 'Max compares the public logbook with the order on the screen.', { mood: 'log_comparison', tag: 'ranking_log_check' }),
      b('Tussen beide versies vindt hij kleine pakketten met een kroonmerk.', 'Entre les deux versions, il trouve de petits paquets marqués d’une couronne.', 'Between the two versions, he finds small packets marked with a crown.', { mood: 'priority_packets_found', tag: 'crown_priority_packets' }),
      b('Robbie begrijpt dat rangschikken haar keuze al vóór een besluit beïnvloedt.', 'Robbie comprend que le classement influence son choix avant la décision.', 'Robbie understands that ranking influences her choice before a decision.', { mood: 'autonomy_insight', tag: 'ranking_is_control' }),
      b('Ze scheidt echte dringendheid voortaan van de macht van een afzender.', 'Elle sépare désormais l’urgence réelle du pouvoir d’un expéditeur.', 'She now separates real urgency from a sender’s power.', { mood: 'clear_priority_rule', tag: 'urgency_not_sender' }),
      b('Alleen de onbevoegde prioriteitspakketten worden tijdelijk bevroren.', 'Seuls les paquets de priorité non autorisés sont temporairement bloqués.', 'Only the unauthorized priority packets are temporarily frozen.', { mood: 'targeted_freeze', tag: 'freeze_private_priority' }),
      b('De waarschuwing van het zorgcentrum staat weer terecht bovenaan.', 'L’alerte du centre de soins retrouve sa juste place en tête.', 'The care-center warning correctly returns to the top.', { mood: 'rightful_order', tag: 'care_alert_restored' }),
      b('Kroon stuurt een sterkere puls die het hele scherm blauw maakt.', 'Couronne envoie une impulsion plus forte qui colore tout l’écran en bleu.', 'Crown sends a stronger pulse that turns the whole screen blue.', { location: 'hidden_transmitter', characters: ['kroon', 'robbie'], mood: 'stronger_control', tag: 'strong_blue_pulse' }),
      b('Robbie noemt het in het logboek een poging om haar keuze te sturen.', 'Robbie l’inscrit dans le journal comme une tentative de diriger son choix.', 'Robbie records it in the logbook as an attempt to steer her choice.', { mood: 'named_violation', tag: 'autonomy_violation_logged' }),
    ],
  },
  {
    titles: t('Alles uit?', 'Tout éteindre ?', 'Turn Everything Off?'),
    range: [89, 100], location: 'network_center', characters: ['robbie', 'fien', 'mats', 'noor'],
    principle: t('beschermen vraagt Robbies stem én bescherming van basisdiensten', 'protéger exige la voix de Robbie et la protection des services essentiels', 'protection requires Robbie’s voice and protection of essential services'),
    beats: [
      b('Fien wil de hoofdkabel lostrekken voordat Kroon opnieuw kan sturen.', 'Fanny veut débrancher le câble principal avant une nouvelle attaque de Couronne.', 'Faye wants to unplug the main cable before Crown can steer again.', { mood: 'protective_urgency', tag: 'unplug_proposal' }),
      b('Robbie erkent het gevaar, maar vraagt om mee te beslissen.', 'Robbie reconnaît le danger, mais demande à participer à la décision.', 'Robbie acknowledges the danger but asks to share in the decision.', { mood: 'autonomy_request', tag: 'robbie_must_decide' }),
      b('Een volledige afsluiting zou ook een monorailwaarschuwing onderbreken.', 'Une coupure totale interromprait aussi une alerte du monorail.', 'A complete shutdown would also interrupt a monorail warning.', { location: 'monorail_platform', mood: 'service_risk', tag: 'monorail_warning' }),
      b('Het zorgnet zou tegelijk de stand van noodvoorraden verliezen.', 'Le réseau de soins perdrait aussi l’état des réserves d’urgence.', 'The care network would also lose the status of emergency supplies.', { location: 'privacy_alcove', mood: 'care_network_risk', tag: 'care_supply_status' }),
      b('Veel bewoners vertrouwen op die twee openbare verbindingen.', 'De nombreux habitants dépendent de ces deux connexions publiques.', 'Many residents depend on those two public connections.', { characters: ['robbie', 'fien', 'mats', 'residents'], mood: 'shared_dependence', tag: 'residents_depend' }),
      b('Noor stelt voor om alleen het verdachte kanaal te isoleren.', 'Nora propose d’isoler seulement le canal suspect.', 'Nora proposes isolating only the suspicious channel.', { mood: 'partial_isolation', tag: 'noor_partial_plan' }),
      b('Robbie vraagt alle opties, risico’s en stopknoppen op één kaart.', 'Robbie demande toutes les options, les risques et les arrêts sur une carte.', 'Robbie asks for every option, risk, and stop control on one map.', { mood: 'informed_choice', tag: 'all_options_map' }),
      b('De vrienden tekenen welke verbindingen noodzakelijk en welke verdacht zijn.', 'Les amis dessinent quelles connexions sont essentielles ou suspectes.', 'The friends map which connections are essential and which are suspicious.', { mood: 'network_mapping', tag: 'essential_route_map' }),
      b('Mats bouwt een lokale buffer voor waarschuwingen tijdens de ingreep.', 'Mathis construit une mémoire locale pour les alertes pendant l’intervention.', 'Max builds a local buffer for warnings during the operation.', { mood: 'safe_buffer', tag: 'local_alert_buffer' }),
      b('Met Robbies toestemming sluiten ze alleen Kroons privékanaal af.', 'Avec l’accord de Robbie, ils ferment seulement le canal privé de Couronne.', 'With Robbie’s permission, they close only Crown’s private channel.', { mood: 'consented_disconnect', tag: 'private_channel_closed' }),
      b('De monorail ontvangt op tijd een waarschuwing over een geblokkeerd spoor.', 'Le monorail reçoit à temps une alerte sur une voie bloquée.', 'The monorail receives a warning about a blocked track in time.', { location: 'monorail_platform', mood: 'public_service_saved', tag: 'monorail_alert_delivered' }),
      b('De stad blijft online, maar Kroons zender zoekt een nieuwe ingang.', 'La ville reste connectée, mais l’émetteur de Couronne cherche une nouvelle entrée.', 'The city stays online, but Crown’s transmitter searches for a new entrance.', { location: 'hidden_transmitter', characters: ['kroon'], mood: 'threat_continues', tag: 'transmitter_searches' }),
    ],
  },
  {
    titles: t('De keten van vertrouwen', 'La chaîne de confiance', 'The Chain of Trust'),
    range: [101, 113], location: 'source_wall', characters: ['robbie', 'fien', 'mats', 'noor'],
    principle: t('belangrijke meldingen vragen twee bronnen, een spoor en een naam', 'les messages importants exigent deux sources, une trace et un nom', 'important reports need two sources, a trail, and a name'),
    beats: [
      b('De groep bouwt een tijdelijke keten voor iedere belangrijke melding.', 'Le groupe construit une chaîne temporaire pour chaque message important.', 'The group builds a temporary chain for every important report.', { mood: 'trust_chain_begins', tag: 'trust_chain_plan' }),
      b('Het eerste vak vraagt om twee werkelijk onafhankelijke bronnen.', 'La première case demande deux sources réellement indépendantes.', 'The first box requires two truly independent sources.', { mood: 'two_source_rule', tag: 'two_sources' }),
      b('Het tweede vak toont wie de oorspronkelijke informatie maakte.', 'La deuxième case montre qui a créé l’information originale.', 'The second box shows who created the original information.', { mood: 'visible_origin', tag: 'source_owner' }),
      b('Het derde vak bewaart elke verkorting, toevoeging en correctie.', 'La troisième case conserve chaque raccourci, ajout et correction.', 'The third box keeps every shortening, addition, and correction.', { mood: 'visible_changes', tag: 'change_log' }),
      b('Het laatste vak noemt een mens die verantwoordelijkheid draagt.', 'La dernière case nomme une personne qui porte la responsabilité.', 'The final box names a person who carries responsibility.', { mood: 'human_accountability', tag: 'responsible_human' }),
      b('Een nieuw weerbericht wordt als eerste proef door de keten gestuurd.', 'Un nouveau bulletin météo traverse la chaîne comme premier test.', 'A new weather report is sent through the chain as the first test.', { mood: 'chain_test', tag: 'weather_chain_test' }),
      b('Eén bron voorspelt regen terwijl de tweede nog droogte meet.', 'Une source annonce de la pluie tandis que la seconde mesure encore du temps sec.', 'One source predicts rain while the second still measures dry weather.', { mood: 'source_disagreement', tag: 'weather_disagreement' }),
      b('Aïcha controleert buiten de wolken en een handmatige regenmeter.', 'Aïcha vérifie dehors les nuages et un pluviomètre manuel.', 'Aisha checks the clouds and a manual rain gauge outside.', { characters: ['robbie', 'aicha', 'fien', 'mats'], mood: 'ground_check', tag: 'physical_weather_check' }),
      b('De melding verschijnt later, maar bevat nu een eerlijke onzekerheid.', 'Le message paraît plus tard, mais contient maintenant une incertitude honnête.', 'The report appears later but now contains honest uncertainty.', { mood: 'slower_reliable', tag: 'honest_weather_report' }),
      b('Ook het zorgcentrum gebruikt de keten voor een nieuwe voorraadroute.', 'Le centre de soins utilise aussi la chaîne pour une nouvelle route de livraison.', 'The care center also uses the chain for a new supply route.', { location: 'privacy_alcove', mood: 'care_chain_use', tag: 'care_supply_chain' }),
      b('Lotte maakt de controlepunten zichtbaar op het openbare netwerkbord.', 'Charlotte rend les contrôles visibles sur le tableau public du réseau.', 'Lottie makes the checks visible on the public network board.', { characters: ['mayor', 'robbie', 'fien', 'mats'], mood: 'public_rules', tag: 'public_trust_board' }),
      b('Een vervalste kopie van Kroon geraakt niet voorbij het eerste vak.', 'Une copie falsifiée de Couronne ne dépasse pas la première case.', 'A forged Crown copy does not pass the first box.', { mood: 'forgery_rejected', tag: 'crown_copy_rejected' }),
      b('De keten vormt een tijdelijke bescherming rond de openbare diensten.', 'La chaîne forme une protection temporaire autour des services publics.', 'The chain forms a temporary shield around the public services.', { mood: 'temporary_shield', tag: 'trust_chain_active' }),
    ],
  },
  {
    titles: t('Ik weet het niet zeker', 'Je n’en suis pas certaine', 'I Am Not Sure'),
    range: [114, 125], location: 'network_center', characters: ['robbie', 'fien', 'mats', 'mayor'],
    principle: t('twijfel eerlijk noemen kan veiliger zijn dan een snel antwoord', 'nommer honnêtement le doute peut être plus sûr qu’une réponse rapide', 'honestly naming doubt can be safer than a quick answer'),
    beats: [
      b('Plots vullen tegenstrijdige berichten alle ingangen van de keten.', 'Soudain, des messages contradictoires remplissent toutes les entrées de la chaîne.', 'Suddenly, conflicting reports fill every entrance to the chain.', { mood: 'conflict_flood', tag: 'conflicting_message_flood' }),
      b('Twee waarschuwingen zijn ondertekend en lijken allebei overtuigend.', 'Deux alertes sont signées et semblent toutes deux convaincantes.', 'Two warnings are signed and both look convincing.', { mood: 'dual_credibility', tag: 'two_signed_alerts' }),
      b('De ene vraagt de sluis te sluiten en de andere vraagt haar te openen.', 'L’une demande de fermer l’écluse et l’autre de l’ouvrir.', 'One asks for the lock to close and the other asks for it to open.', { location: 'flood_station', mood: 'opposite_orders', tag: 'open_close_conflict' }),
      b('Kroon eist dat Robbie onmiddellijk één antwoord kiest.', 'Couronne exige que Robbie choisisse immédiatement une réponse.', 'Crown demands that Robbie choose one answer immediately.', { characters: ['kroon', 'robbie', 'fien', 'mats'], mood: 'pressure_to_guess', tag: 'crown_demands_answer' }),
      b('Robbie berekent meerdere uitkomsten, maar geen enkele is voldoende zeker.', 'Robbie calcule plusieurs résultats, mais aucun n’est assez certain.', 'Robbie calculates several outcomes, but none is certain enough.', { mood: 'real_uncertainty', tag: 'no_certain_outcome' }),
      b('‘Ik weet het nog niet zeker,’ zegt Robbie voor de hele stad.', '« Je n’en suis pas encore certaine », dit Robbie devant toute la ville.', '“I am not sure yet,” Robbie tells the whole city.', { mood: 'honest_doubt', tag: 'explicit_uncertainty' }),
      b('Ze kiest voorlopig een kleine afsluiting die snel omkeerbaar blijft.', 'Elle choisit provisoirement une petite fermeture facile à annuler.', 'She temporarily chooses a small closure that remains easy to reverse.', { location: 'flood_station', mood: 'reversible_step', tag: 'small_reversible_closure' }),
      b('De evacuatieploeg blijft klaar zonder bewoners onnodig weg te sturen.', 'L’équipe d’évacuation reste prête sans déplacer inutilement les habitants.', 'The evacuation team stays ready without sending residents away unnecessarily.', { characters: ['robbie', 'mayor', 'residents'], mood: 'prepared_not_panicked', tag: 'evacuation_ready' }),
      b('Aïcha en Mats verzamelen drie nieuwe metingen bij de sluis.', 'Aïcha et Mathis recueillent trois nouvelles mesures près de l’écluse.', 'Aisha and Max collect three new readings at the lock.', { location: 'flood_station', characters: ['aicha', 'mats', 'robbie'], mood: 'new_evidence', tag: 'three_new_measurements' }),
      b('De bron die openen vroeg blijkt een kopie van Kroons zender.', 'La source demandant l’ouverture est une copie de l’émetteur de Couronne.', 'The source asking to open is a copy of Crown’s transmitter.', { mood: 'false_source_exposed', tag: 'transmitter_copy_found' }),
      b('De eerlijke twijfel heeft de stad tijd gegeven om dat bewijs te vinden.', 'Le doute honnête a donné à la ville le temps de trouver cette preuve.', 'Honest doubt gave the city time to find that evidence.', { mood: 'uncertainty_protects', tag: 'doubt_bought_time' }),
      b('Kroon verplaatst zijn afstandscode geruisloos naar de netcentrale.', 'Couronne déplace silencieusement son code à distance vers le centre du réseau.', 'Crown silently moves his remote code into the network center.', { location: 'hidden_transmitter', characters: ['kroon'], mood: 'final_attack_prepared', tag: 'remote_code_moves' }),
    ],
  },
  {
    titles: t('De stille tegenzet', 'La riposte silencieuse', 'The Quiet Countermove'),
    range: [126, 138], location: 'network_center', characters: ['robbie', 'fien', 'mats', 'noor'],
    principle: t('verwijder alleen de onbevoegde sturing en bewaar dienst én bewijs', 'retirer seulement le contrôle non autorisé préserve le service et les preuves', 'removing only unauthorized control preserves both service and evidence'),
    beats: [
      b('Noor ontdekt de afstandscode tussen gewone netwerkregels.', 'Nora découvre le code à distance parmi les règles ordinaires du réseau.', 'Nora finds the remote code hidden among ordinary network rules.', { mood: 'code_discovered', tag: 'remote_code_found' }),
      b('Mats volgt de code naar drie knooppunten met hetzelfde kroonmerk.', 'Mathis suit le code vers trois nœuds portant la même couronne.', 'Max traces the code to three nodes with the same crown mark.', { mood: 'three_nodes', tag: 'three_crown_nodes' }),
      b('Fien verdeelt duidelijke taken onder vervoer, zorg en waterbeheer.', 'Fanny répartit des tâches claires entre transport, soins et gestion de l’eau.', 'Faye assigns clear tasks across transport, care, and water services.', { characters: ['fien', 'robbie', 'residents'], mood: 'service_coordination', tag: 'faye_coordinates' }),
      b('Robbie tekent welke toestemming bij iedere verbinding hoort.', 'Robbie dessine quelle autorisation appartient à chaque connexion.', 'Robbie maps which permission belongs to every connection.', { mood: 'permission_map', tag: 'permission_network_map' }),
      b('Kroons code probeert alle eigen opdrachten bovenaan te zetten.', 'Le code de Couronne tente de placer tous ses ordres en tête.', 'Crown’s code tries to place all of his orders at the top.', { mood: 'attack_begins', tag: 'priority_attack' }),
      b('De lokale buffers houden waarschuwingen zichtbaar tijdens de aanval.', 'Les mémoires locales gardent les alertes visibles pendant l’attaque.', 'The local buffers keep warnings visible during the attack.', { mood: 'buffers_hold', tag: 'local_buffers_hold' }),
      b('Robbie verwijdert alleen de onbevoegde prioriteiten uit de rangorde.', 'Robbie retire seulement les priorités non autorisées du classement.', 'Robbie removes only the unauthorized priorities from the ranking.', { mood: 'precise_countermove', tag: 'remove_unauthorized_priorities' }),
      b('Geen openbare meting, waarschuwing of bron wordt daarbij gewist.', 'Aucune mesure, alerte ou source publique n’est effacée.', 'No public measurement, warning, or source is deleted.', { mood: 'public_data_preserved', tag: 'no_public_deletion' }),
      b('Op Kroons scherm verdwijnen zijn blauwe pijlen één voor één.', 'Sur l’écran de Couronne, ses flèches bleues disparaissent une à une.', 'On Crown’s screen, his blue arrows disappear one by one.', { location: 'kroon_remote_lab', characters: ['kroon'], mood: 'control_lost', tag: 'crown_loses_control' }),
      b('Kroon probeert daarna het volledige logboek op afstand te wissen.', 'Couronne tente ensuite d’effacer tout le journal à distance.', 'Crown then tries to erase the entire logbook remotely.', { mood: 'evidence_attack', tag: 'erase_log_attempt' }),
      b('Drie openbare kopieën bewaren elke wijziging op aparte plaatsen.', 'Trois copies publiques conservent chaque modification à des endroits séparés.', 'Three public copies preserve every change in separate places.', { mood: 'evidence_survives', tag: 'three_public_logs' }),
      b('Lotte verzegelt het bewijs terwijl de diensten normaal blijven werken.', 'Charlotte scelle les preuves pendant que les services continuent normalement.', 'Lottie seals the evidence while the services continue normally.', { characters: ['mayor', 'robbie', 'fien', 'mats'], mood: 'public_accountability', tag: 'evidence_sealed' }),
      b('Het kleinere netwerk wordt rustig, controleerbaar en opnieuw van de stad.', 'Le réseau réduit redevient calme, contrôlable et public.', 'The smaller network becomes calm, controllable, and public again.', { mood: 'network_stable', tag: 'smaller_public_network' }),
    ],
  },
  {
    titles: t('Een sein van ver', 'Un signal lointain', 'A Signal from Far Away'),
    range: [139, 150], location: 'network_center', characters: ['robbie', 'fien', 'mats', 'mayor'],
    principle: t('een goed netwerk toont toestemming én lege plekken', 'un bon réseau montre les autorisations et les espaces vides', 'a good network shows permissions as well as empty spaces'),
    beats: [
      b('Morgenstad opent het kleinere netwerk op het centrale plein.', 'Cité-de-Demain ouvre le réseau réduit sur la place centrale.', 'Tomorrow City opens the smaller network in the central square.', { location: 'morgenstad_market_square', mood: 'public_opening', tag: 'public_network_opens' }),
      b('Iedere verbinding toont voortaan doel, eigenaar en toestemming.', 'Chaque connexion montre désormais son but, son propriétaire et son autorisation.', 'Every connection now shows its purpose, owner, and permission.', { mood: 'clear_permissions', tag: 'permission_labels' }),
      b('Bewoners kunnen hun gegevens bekijken, verbeteren of terugtrekken.', 'Les habitants peuvent consulter, corriger ou retirer leurs données.', 'Residents can view, correct, or withdraw their data.', { location: 'morgenstad_market_square', characters: ['robbie', 'fien', 'mats', 'residents'], mood: 'resident_control', tag: 'resident_data_control' }),
      b('Robbie laat onbekende gebieden bewust leeg op de stadskaart.', 'Robbie laisse volontairement les zones inconnues vides sur la carte.', 'Robbie deliberately leaves unknown areas blank on the city map.', { mood: 'honest_blank_spaces', tag: 'unknowns_stay_blank' }),
      b('De vrienden vieren dat de stad niet blind hoefde te worden.', 'Les amis célèbrent le fait que la ville n’a pas dû devenir aveugle.', 'The friends celebrate that the city did not have to go blind.', { location: 'morgenstad_market_square', mood: 'earned_celebration', tag: 'network_celebration' }),
      b('Robbie controleert nog één keer de digitale afdruk van het kroonmerk.', 'Robbie vérifie encore une fois l’empreinte numérique de la couronne.', 'Robbie checks the digital fingerprint of the crown mark one more time.', { mood: 'final_check', tag: 'crown_fingerprint_check' }),
      b('Tussen rustige meetpunten verschijnt plots een zwak ver sein.', 'Parmi les mesures calmes apparaît soudain un faible signal lointain.', 'A faint distant signal suddenly appears among the calm readings.', { mood: 'distant_mystery', tag: 'distant_signal' }),
      b('De kaart plaatst het sein bij een mijnstreek ver buiten Morgenstad.', 'La carte place le signal près d’une région minière loin de Cité-de-Demain.', 'The map places the signal near a mining region far beyond Tomorrow City.', { mood: 'far_mine_region', tag: 'mine_region' }),
      b('Het pakket draagt hetzelfde merkteken als Kroons verborgen meetkasten.', 'Le paquet porte la même marque que les boîtiers cachés de Couronne.', 'The packet carries the same mark as Crown’s hidden meter boxes.', { mood: 'same_mark', tag: 'same_crown_mark' }),
      b('Binnenin staat geen bevel, maar een korte vraag om hulp.', 'À l’intérieur, il n’y a pas d’ordre, mais une courte demande d’aide.', 'Inside is not a command but a short request for help.', { mood: 'unexpected_request', tag: 'request_for_help' }),
      b('De vrienden besluiten eerst bron, datum en afstand te controleren.', 'Les amis décident de vérifier d’abord la source, la date et la distance.', 'The friends decide to check the source, date, and distance first.', { mood: 'careful_next_step', tag: 'verify_before_journey' }),
      b('Ver weg knippert een mijnlamp driemaal onder hetzelfde kroonmerk.', 'Au loin, une lampe de mine clignote trois fois sous la même couronne.', 'Far away, a mine light flashes three times beneath the same crown mark.', { location: 'hidden_transmitter', characters: ['robbie'], mood: 'next_book_signal', tag: 'mine_light_cliffhanger' }),
    ],
  },
];

function pageText(event, principle, pageNumber) {
  const index = (pageNumber - 1) % support.observations.length;
  const rule = {
    'nl-BE': `${support.ruleIntros[index]['nl-BE']}: ${principle['nl-BE']}.`,
    'fr-BE': `${support.ruleIntros[index]['fr-BE']} : ${principle['fr-BE']}.`,
    'en-US': `${support.ruleIntros[index]['en-US']}: ${principle['en-US']}.`,
  };
  return Object.fromEntries(['nl-BE', 'fr-BE', 'en-US'].map((locale) => [
    locale,
    [
      event[locale],
      support.observations[index][locale],
      support.questions[index][locale],
      rule[locale],
      support.actions[index][locale],
    ].join('\n'),
  ]));
}

if (chapters.length !== 12) throw new Error(`Expected 12 chapters, found ${chapters.length}`);
fs.mkdirSync(outputRoot, { recursive: true });
let totalPages = 0;
for (let chapterIndex = 0; chapterIndex < chapters.length; chapterIndex += 1) {
  const chapter = chapters[chapterIndex];
  const chapterNumber = chapterIndex + 1;
  const [startPage, endPage] = chapter.range;
  if (chapter.beats.length !== endPage - startPage + 1) throw new Error(`Chapter ${chapterNumber} has ${chapter.beats.length} beats`);
  const pages = chapter.beats.map((beat, beatIndex) => {
    const pageNumber = startPage + beatIndex;
    const padded = String(pageNumber).padStart(3, '0');
    return {
      pageNumber,
      storyBeatId: `L2_E4_C${String(chapterNumber).padStart(2, '0')}_P${padded}_${(beat.tag ?? 'NETWORK_STEP').toUpperCase()}`,
      text: pageText(beat.event, chapter.principle, pageNumber),
      characters: beat.characters ?? chapter.characters,
      location: beat.location ?? chapter.location,
      visual: beat.event['en-US'],
      mood: beat.mood ?? 'careful_network_reasoning',
      continuityTags: [`chapter_${String(chapterNumber).padStart(2, '0')}`, beat.tag ?? 'network_step'],
    };
  });
  totalPages += pages.length;
  const source = { chapterNumber, titles: chapter.titles, startPage, endPage, pages };
  fs.writeFileSync(path.join(outputRoot, `ch${String(chapterNumber).padStart(2, '0')}.json`), `${JSON.stringify(source, null, 2)}\n`, 'utf8');
}

if (totalPages !== 150) throw new Error(`Expected 150 total pages, found ${totalPages}`);
console.log(JSON.stringify({ chapters: chapters.length, pages: totalPages, outputRoot: path.relative(root, outputRoot).split(path.sep).join('/') }, null, 2));
