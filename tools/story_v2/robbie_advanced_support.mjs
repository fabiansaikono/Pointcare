import fs from 'node:fs';
import path from 'node:path';

export const t = (nl, fr, en) => ({ 'nl-BE': nl, 'fr-BE': fr, 'en-US': en });
export const b = (nl, fr, en, options = {}) => ({ event: t(nl, fr, en), ...options });

const observations = [
  t('Robbie zet de nieuwe meting naast het volledige logboek.', 'Robbie place la nouvelle mesure à côté du journal complet.', 'Robbie places the new measurement beside the complete logbook.'),
  t('Op haar kaart blijven ontbrekende gegevens duidelijk als leegte zichtbaar.', 'Sur sa carte, les données manquantes restent clairement visibles comme des vides.', 'On her map, missing data remains clearly visible as empty space.'),
  t('De vrienden volgen stap voor stap hoe het gevolg is ontstaan.', 'Les amis suivent pas à pas la manière dont la conséquence est apparue.', 'The friends follow step by step how the consequence arose.'),
  t('Een tweede blik toont details die in de snelle samenvatting ontbraken.', 'Un second regard révèle des détails absents du résumé rapide.', 'A second look reveals details missing from the quick summary.'),
  t('Robbie onderscheidt wat gemeten, verteld en alleen vermoed werd.', 'Robbie distingue ce qui a été mesuré, raconté ou seulement supposé.', 'Robbie separates what was measured, reported, and merely assumed.'),
  t('Mats controleert de techniek zonder de menselijke gevolgen te vergeten.', 'Mathis vérifie la technique sans oublier les conséquences humaines.', 'Max checks the technology without forgetting the human consequences.'),
  t('Fien zoekt wie door de beslissing voordeel of nadeel krijgt.', 'Fanny cherche qui gagne ou perd à cause de la décision.', 'Faye looks for who gains or loses because of the decision.'),
  t('Het model geeft een helder antwoord, maar niet de hele werkelijkheid.', 'Le modèle donne une réponse claire, mais pas toute la réalité.', 'The model gives a clear answer, but not the whole reality.'),
  t('De groep vergelijkt de voorspelling met wat ter plaatse gebeurt.', 'Le groupe compare la prévision avec ce qui se passe sur place.', 'The group compares the prediction with what happens on the ground.'),
  t('Robbie bewaart zowel het resultaat als de gekozen uitgangspunten.', 'Robbie conserve le résultat ainsi que les hypothèses choisies.', 'Robbie keeps both the result and the chosen assumptions.'),
  t('Een kleine afwijking blijkt belangrijk zodra iedereen verder kijkt.', 'Un petit écart devient important lorsque chacun regarde plus loin.', 'A small difference becomes important once everyone looks further.'),
  t('De kaart toont verbanden, maar noemt nog geen schuldige.', 'La carte montre des liens, mais ne désigne encore aucun coupable.', 'The map shows connections but does not name anyone guilty yet.'),
  t('Robbie controleert of de nieuwste versie nog dezelfde betekenis draagt.', 'Robbie vérifie si la version récente garde encore le même sens.', 'Robbie checks whether the newest version still carries the same meaning.'),
];

const questions = [
  t('‘Welk bewijs kan deze conclusie nog veranderen?’ vraagt Fien.', '« Quelle preuve pourrait encore modifier cette conclusion ? » demande Fanny.', '“What evidence could still change this conclusion?” Faye asks.'),
  t('Mats vraagt welke grens het systeem beslist niet mag overschrijden.', 'Mathis demande quelle limite le système ne doit jamais franchir.', 'Max asks which boundary the system must never cross.'),
  t('‘Wie draagt de gevolgen als onze voorspelling fout is?’ vraagt Fien.', '« Qui subira les conséquences si notre prévision est fausse ? » demande Fanny.', '“Who faces the consequences if our prediction is wrong?” Faye asks.'),
  t('De vrienden vragen of dezelfde uitkomst ook een andere oorzaak kan hebben.', 'Les amis demandent si le même résultat peut avoir une autre cause.', 'The friends ask whether the same outcome could have another cause.'),
  t('Mats wil weten welke gegevens buiten het model zijn gebleven.', 'Mathis veut savoir quelles données sont restées hors du modèle.', 'Max wants to know which data was left outside the model.'),
  t('Fien vraagt wie toestemming gaf en wie die toestemming kan intrekken.', 'Fanny demande qui a donné l’autorisation et qui peut la retirer.', 'Faye asks who gave permission and who can withdraw it.'),
  t('‘Kunnen we deze stap veilig terugdraaien?’ vraagt Robbie zelf.', '« Pouvons-nous annuler cette étape sans danger ? » demande Robbie.', '“Can we safely reverse this step?” Robbie asks herself.'),
  t('De groep vraagt of snelheid hier werkelijk belangrijker is dan overleg.', 'Le groupe demande si la vitesse compte vraiment plus que la concertation.', 'The group asks whether speed truly matters more than consultation here.'),
  t('Mats vraagt wie het systeem kan stoppen wanneer Robbie zich vergist.', 'Mathis demande qui peut arrêter le système si Robbie se trompe.', 'Max asks who can stop the system if Robbie is mistaken.'),
  t('Fien wil weten waarom sommige stemmen minder zichtbaar zijn.', 'Fanny veut savoir pourquoi certaines voix sont moins visibles.', 'Faye wants to know why some voices are less visible.'),
  t('‘Welke schade tellen we wel, en welke niet?’ vraagt Robbie.', '« Quels dommages comptons-nous, et lesquels oublions-nous ? » demande Robbie.', '“Which harm do we count, and which harm do we miss?” Robbie asks.'),
  t('De vrienden vragen welke persoon verantwoordelijk blijft voor het besluit.', 'Les amis demandent quelle personne reste responsable de la décision.', 'The friends ask which person remains responsible for the decision.'),
  t('Mats vraagt of een veilig doel ook met minder macht bereikt kan worden.', 'Mathis demande si un objectif sûr peut être atteint avec moins de pouvoir.', 'Max asks whether a safe goal can be reached with less power.'),
];

const comparisons = [
  t('Een snelle oplossing voorkomt vandaag schade, maar kan morgen vrijheid kosten.', 'Une solution rapide évite un dommage aujourd’hui, mais peut coûter de la liberté demain.', 'A quick solution prevents harm today but may cost freedom tomorrow.'),
  t('Twee gelijke cijfers blijken voor verschillende mensen iets anders te betekenen.', 'Deux chiffres identiques prennent un sens différent selon les personnes.', 'Two equal numbers turn out to mean different things for different people.'),
  t('De veiligste berekening is niet automatisch de eerlijkste keuze.', 'Le calcul le plus sûr n’est pas automatiquement le choix le plus juste.', 'The safest calculation is not automatically the fairest choice.'),
  t('Wat op afstand klein lijkt, kan ter plaatse een volledig leven raken.', 'Ce qui semble petit à distance peut toucher toute une vie sur place.', 'What looks small from far away can affect an entire life on the ground.'),
  t('Een oude fout verklaart een deel van de schade, maar niet de nieuwe sporen.', 'Une ancienne erreur explique une partie des dégâts, mais pas les nouvelles traces.', 'An old mistake explains part of the damage but not the new traces.'),
  t('Het plan bespaart middelen, terwijl de verborgen kosten bij anderen terechtkomen.', 'Le plan économise des ressources, tandis que les coûts cachés touchent les autres.', 'The plan saves resources while hidden costs fall on other people.'),
  t('Kroons voorstel klinkt ordelijk, maar zijn controle blijft gesloten.', 'La proposition de Couronne paraît ordonnée, mais son contrôle reste fermé.', 'Crown’s proposal sounds orderly, but his control remains closed.'),
  t('De cijfers tonen risico, terwijl de verhalen tonen wie kan veranderen.', 'Les chiffres montrent le risque, tandis que les récits montrent qui peut changer.', 'The numbers show risk while the stories show who can change.'),
  t('Meer toezicht verlaagt sommige gevaren en maakt andere schade onzichtbaar.', 'Davantage de surveillance réduit certains dangers et cache d’autres dommages.', 'More surveillance lowers some dangers and hides other harm.'),
  t('Een technisch juiste stap kan moreel toch onaanvaardbaar zijn.', 'Une étape techniquement correcte peut rester moralement inacceptable.', 'A technically correct step can still be morally unacceptable.'),
  t('De groep vergelijkt de beste uitkomst met de minst ingrijpende maatregel.', 'Le groupe compare le meilleur résultat avec la mesure la moins intrusive.', 'The group compares the best outcome with the least intrusive measure.'),
  t('Wat niet meetbaar is, verdwijnt gemakkelijk uit een perfecte tabel.', 'Ce qui n’est pas mesurable disparaît facilement d’un tableau parfait.', 'What cannot be measured easily disappears from a perfect table.'),
  t('Een besluit kan doeltreffend zijn en toch te veel macht verzamelen.', 'Une décision peut être efficace tout en concentrant trop de pouvoir.', 'A decision can be effective while still gathering too much power.'),
];

const responses = [
  t('Robbie noemt eerst haar onzekerheid voordat ze een advies geeft.', 'Robbie nomme d’abord son incertitude avant de donner un conseil.', 'Robbie names her uncertainty before giving advice.'),
  t('Ze laat een mens met verantwoordelijkheid het noodsein bevestigen.', 'Elle fait confirmer l’alerte par une personne responsable.', 'She has a responsible person confirm the emergency signal.'),
  t('Robbie weigert een onbekend vak als nul te behandelen.', 'Robbie refuse de traiter une case inconnue comme un zéro.', 'Robbie refuses to treat an unknown box as zero.'),
  t('Ze legt haar reden uit, zodat anderen haar kunnen tegenspreken.', 'Elle explique sa raison afin que les autres puissent la contester.', 'She explains her reason so others can challenge her.'),
  t('Robbie kiest een tijdelijke stap in plaats van een definitieve ingreep.', 'Robbie choisit une étape temporaire plutôt qu’une intervention définitive.', 'Robbie chooses a temporary step instead of a permanent intervention.'),
  t('Ze vraagt toestemming voordat ze een nieuwe verbinding opent.', 'Elle demande une autorisation avant d’ouvrir une nouvelle connexion.', 'She asks permission before opening a new connection.'),
  t('Robbie bewaart het oorspronkelijke bewijs naast iedere latere wijziging.', 'Robbie garde la preuve originale à côté de chaque modification ultérieure.', 'Robbie keeps the original evidence beside every later change.'),
  t('Ze scheidt het onmiddellijke gevaar van een mogelijk toekomstig risico.', 'Elle sépare le danger immédiat d’un risque futur possible.', 'She separates immediate danger from a possible future risk.'),
  t('Robbie laat de keuze voorlopig totdat de ontbrekende feiten bekend zijn.', 'Robbie garde le choix provisoire jusqu’à connaître les faits manquants.', 'Robbie keeps the choice provisional until the missing facts are known.'),
  t('Ze verdeelt de taak, zodat niet alle controle bij haar blijft.', 'Elle répartit la tâche afin de ne pas garder tout le contrôle.', 'She divides the task so all control does not remain with her.'),
  t('Robbie erkent dat haar berekening binnen gekozen grenzen geldig is.', 'Robbie reconnaît que son calcul n’est valable que dans les limites choisies.', 'Robbie acknowledges that her calculation is valid only within chosen limits.'),
  t('Ze stopt alleen het schadelijke onderdeel en bewaart de nuttige dienst.', 'Elle arrête seulement la partie nuisible et conserve le service utile.', 'She stops only the harmful part and preserves the useful service.'),
  t('Robbie vraagt om een controle die ook haar eigen macht begrenst.', 'Robbie demande un contrôle qui limite aussi son propre pouvoir.', 'Robbie asks for a safeguard that also limits her own power.'),
];

const actions = [
  t('Daarna testen ze de kleinste stap die het probleem werkelijk kan beperken.', 'Ensuite, ils testent la plus petite étape capable de limiter le problème.', 'Then they test the smallest step that can truly limit the problem.'),
  t('Het team houdt een tweede veilige route klaar voor onverwachte gevolgen.', 'L’équipe garde une deuxième voie sûre pour les conséquences imprévues.', 'The team keeps a second safe route ready for unexpected consequences.'),
  t('Iedere verandering verschijnt meteen in een openbaar en controleerbaar logboek.', 'Chaque changement apparaît aussitôt dans un journal public et contrôlable.', 'Every change immediately appears in a public and reviewable logbook.'),
  t('De vrienden controleren de plaats zelf voordat het systeem verdergaat.', 'Les amis vérifient eux-mêmes le lieu avant que le système continue.', 'The friends check the location themselves before the system continues.'),
  t('Een onafhankelijke meter blijft actief tijdens de hele ingreep.', 'Un compteur indépendant reste actif pendant toute l’intervention.', 'An independent meter remains active throughout the intervention.'),
  t('Ze spreken af wie de proef onmiddellijk mag stoppen.', 'Ils décident qui peut arrêter immédiatement l’essai.', 'They agree on who may stop the trial immediately.'),
  t('De bewoners krijgen tijd om bewijs, bezwaren en alternatieven te delen.', 'Les habitants ont le temps de partager preuves, objections et alternatives.', 'Residents receive time to share evidence, objections, and alternatives.'),
  t('De groep beschermt eerst leven en onderzoekt daarna wie verantwoordelijk is.', 'Le groupe protège d’abord la vie puis cherche les responsabilités.', 'The group protects life first and investigates responsibility afterward.'),
  t('Mats bouwt een begrenzing die geen verborgen beheerderstoegang bevat.', 'Mathis construit une limite sans accès secret pour un administrateur.', 'Max builds a boundary with no hidden administrator access.'),
  t('Fien verdeelt duidelijke taken en controleert of niemand wordt vergeten.', 'Fanny répartit des tâches claires et vérifie que personne n’est oublié.', 'Faye assigns clear tasks and checks that nobody is forgotten.'),
  t('Robbie bewaart genoeg ruimte om de beslissing later te herzien.', 'Robbie garde assez de marge pour revoir la décision plus tard.', 'Robbie preserves enough room to reconsider the decision later.'),
  t('De raad kiest pas na overleg de minst ingrijpende werkzame maatregel.', 'Le conseil choisit après concertation la mesure efficace la moins intrusive.', 'Only after discussion does the council choose the least intrusive effective measure.'),
  t('Het resultaat blijft tijdelijk totdat een nieuwe controle het bevestigt.', 'Le résultat reste temporaire jusqu’à confirmation par un nouveau contrôle.', 'The result remains temporary until a new check confirms it.'),
];

const consequences = [
  t('Daardoor blijft hulp mogelijk terwijl het onderzoek verdergaat.', 'Ainsi, l’aide reste possible pendant que l’enquête continue.', 'This keeps help possible while the investigation continues.'),
  t('De vertraging kost tijd, maar voorkomt een grotere en blijvende fout.', 'Le retard coûte du temps, mais évite une erreur plus grave et durable.', 'The delay costs time but prevents a larger and lasting mistake.'),
  t('Niemand krijgt volledige zekerheid, maar iedereen ziet de reden van de keuze.', 'Personne n’obtient une certitude totale, mais chacun voit la raison du choix.', 'Nobody gains complete certainty, but everyone can see the reason for the choice.'),
  t('Een verborgen nadeel wordt zichtbaar voordat het onomkeerbaar wordt.', 'Un désavantage caché devient visible avant d’être irréversible.', 'A hidden disadvantage becomes visible before it becomes irreversible.'),
  t('De groep kan bijsturen zonder het hele systeem uit te schakelen.', 'Le groupe peut corriger la route sans éteindre tout le système.', 'The group can adjust course without shutting down the whole system.'),
  t('Het plan wordt iets minder snel en veel beter controleerbaar.', 'Le plan devient un peu moins rapide et beaucoup plus contrôlable.', 'The plan becomes slightly slower and far easier to review.'),
  t('Ook de mensen buiten de oorspronkelijke gegevens krijgen een stem.', 'Les personnes absentes des données initiales obtiennent aussi une voix.', 'People missing from the original data also receive a voice.'),
  t('Het bewijs blijft bewaard voor herstel, beroep en latere controle.', 'Les preuves restent conservées pour la réparation, le recours et le contrôle.', 'The evidence remains preserved for repair, appeal, and later review.'),
  t('Robbie verliest een deel van haar snelheid, maar wint betrouwbare tegenspraak.', 'Robbie perd une partie de sa vitesse, mais gagne une opposition fiable.', 'Robbie loses some speed but gains reliable challenge.'),
  t('De gekozen grens beschermt zowel de stad als de vrijheid om te reageren.', 'La limite choisie protège la ville et la liberté de réagir.', 'The chosen boundary protects both the city and the freedom to respond.'),
  t('Een kleine fout wordt gecorrigeerd voordat zij veel mensen raakt.', 'Une petite erreur est corrigée avant de toucher de nombreuses personnes.', 'A small error is corrected before it affects many people.'),
  t('Kroon verliest een voordeel omdat zijn gesloten controle zichtbaar wordt.', 'Couronne perd un avantage lorsque son contrôle fermé devient visible.', 'Crown loses an advantage when his closed control becomes visible.'),
  t('De eindbeslissing blijft uitlegbaar, tijdelijk en aan voorwaarden gebonden.', 'La décision finale reste explicable, temporaire et soumise à des conditions.', 'The final decision remains explainable, temporary, and bound by conditions.'),
];

const perspectives = [
  t('Aïcha bekijkt dezelfde kaart vanuit de gevolgen voor water en natuur.', 'Aïcha regarde la même carte à travers les conséquences pour l’eau et la nature.', 'Aisha views the same map through its effects on water and nature.'),
  t('Noor ziet een technisch succes, maar ook een oude fout in haar ontwerp.', 'Nora voit une réussite technique, mais aussi une ancienne erreur de conception.', 'Nora sees a technical success but also an old design mistake.'),
  t('Mats bewondert de oplossing en blijft tegelijk bang om overbodig te worden.', 'Mathis admire la solution tout en craignant de devenir inutile.', 'Max admires the solution while still fearing he may become unnecessary.'),
  t('Fien begrijpt de haast, maar verdedigt wie niet gehoord werd.', 'Fanny comprend l’urgence, mais défend ceux qui n’ont pas été entendus.', 'Faye understands the urgency but defends those who were not heard.'),
  t('Een bewoner ziet geen model, maar wel wat er thuis verandert.', 'Un habitant ne voit pas de modèle, mais voit ce qui change chez lui.', 'A resident sees no model but does see what changes at home.'),
  t('Lotte moet veiligheid, rechten en verantwoordelijkheid tegelijk afwegen.', 'Charlotte doit peser sécurité, droits et responsabilité en même temps.', 'Lottie must weigh safety, rights, and responsibility at the same time.'),
  t('Robbie ziet een patroon dat voor haar vrienden uit losse verhalen bestaat.', 'Robbie voit un motif composé de récits séparés pour ses amis.', 'Robbie sees a pattern that appears to her friends as separate stories.'),
  t('Kroon noemt overleg zwak, omdat hij alleen het snelste resultaat telt.', 'Couronne traite la concertation de faiblesse, car il ne compte que la vitesse.', 'Crown calls consultation weak because he counts only the fastest result.'),
  t('De hulpverleners vrezen stilstand, terwijl bewoners willekeur vrezen.', 'Les secours craignent l’arrêt, tandis que les habitants craignent l’arbitraire.', 'Emergency workers fear delay while residents fear arbitrary control.'),
  t('De raad kijkt niet alleen naar gevaar, maar ook naar rechten en herstel.', 'Le conseil examine le danger, mais aussi les droits et la réparation.', 'The council considers danger as well as rights and repair.'),
  t('Fien en Mats verschillen van mening zonder elkaars zorg te ontkennen.', 'Fanny et Mathis ne sont pas d’accord sans nier leurs préoccupations.', 'Faye and Max disagree without dismissing each other’s concern.'),
  t('Robbie begrijpt het bezwaar, maar vreest de schade van te laat handelen.', 'Robbie comprend l’objection, mais craint les dégâts d’une action trop tardive.', 'Robbie understands the objection but fears the harm of acting too late.'),
  t('Zelfs een tegenstander behoudt een stem, zonder daardoor het besluit te bepalen.', 'Même un adversaire garde une voix sans décider seul du résultat.', 'Even an opponent keeps a voice without controlling the decision.'),
];

const safeguards = [
  t('Een externe stopknop blijft bereikbaar voor drie onafhankelijke beheerders.', 'Un arrêt externe reste accessible à trois responsables indépendants.', 'An external stop remains available to three independent stewards.'),
  t('Geen enkele sleutel kan het systeem voortaan alleen activeren.', 'Aucune clé ne peut désormais activer seule le système.', 'No single key can activate the system from now on.'),
  t('De toestemming vervalt automatisch zodra de afgesproken tijd voorbij is.', 'L’autorisation expire automatiquement après la durée convenue.', 'Permission automatically expires when the agreed time has passed.'),
  t('Iedere getroffen persoon krijgt uitleg, bezwaar en een veilige uitweg.', 'Chaque personne touchée reçoit une explication, un recours et une sortie sûre.', 'Every affected person receives an explanation, an appeal, and a safe exit.'),
  t('Een onafhankelijke controle vergelijkt het besluit met het openbare bewijs.', 'Un contrôle indépendant compare la décision avec les preuves publiques.', 'An independent review compares the decision with the public evidence.'),
  t('Het systeem bewaart geen geheime kopie buiten het gedeelde logboek.', 'Le système ne garde aucune copie secrète hors du journal partagé.', 'The system keeps no secret copy outside the shared logbook.'),
  t('De noodstap stopt zodra het directe levensgevaar verdwenen is.', 'La mesure d’urgence s’arrête dès que le danger vital disparaît.', 'The emergency step stops as soon as the immediate danger has passed.'),
  t('Een terugkeerproef blijft verplicht voordat de ingreep groter mag worden.', 'Un test de retour reste obligatoire avant toute extension.', 'A return test remains required before the intervention may expand.'),
  t('Robbies eigen toegang wordt op dezelfde manier gelogd als die van mensen.', 'L’accès de Robbie est enregistré comme celui des humains.', 'Robbie’s own access is logged in the same way as human access.'),
  t('De raad kan de opdracht pauzeren zonder bewijs of zorg uit te schakelen.', 'Le conseil peut suspendre l’ordre sans couper les preuves ni les soins.', 'The council can pause the order without disabling evidence or care.'),
  t('De minst ingrijpende werkzame maatregel krijgt altijd voorrang.', 'La mesure efficace la moins intrusive reçoit toujours la priorité.', 'The least intrusive effective measure always receives priority.'),
  t('Wie de maatregel uitvoert, mag niet alleen over het beroep beslissen.', 'Celui qui applique la mesure ne peut décider seul du recours.', 'Whoever applies the measure may not decide the appeal alone.'),
  t('Alle grenzen gelden ook wanneer Robbie zeker denkt te zijn.', 'Toutes les limites s’appliquent même lorsque Robbie se croit certaine.', 'Every boundary applies even when Robbie believes she is certain.'),
];

const uncertainties = [
  t('Robbie kan de kans berekenen, maar niet voorspellen wie later berouw krijgt.', 'Robbie peut calculer la probabilité, mais pas prévoir qui regrettera plus tard.', 'Robbie can calculate probability but cannot predict who will later feel remorse.'),
  t('De gegevens tonen het verleden en laten toekomstige keuzes gedeeltelijk open.', 'Les données montrent le passé et laissent les choix futurs en partie ouverts.', 'The data shows the past and leaves future choices partly open.'),
  t('Niemand weet zeker of de tijdelijke oplossing alle neveneffecten voorkomt.', 'Personne ne sait si la solution temporaire évite tous les effets secondaires.', 'Nobody knows whether the temporary solution prevents every side effect.'),
  t('Een ontbrekende stem kan het morele gewicht van het besluit veranderen.', 'Une voix manquante peut modifier le poids moral de la décision.', 'A missing voice can change the moral weight of the decision.'),
  t('De raad erkent dat ook een zorgvuldig protocol later verbetering nodig heeft.', 'Le conseil reconnaît qu’un protocole prudent devra peut-être évoluer.', 'The council acknowledges that even a careful protocol may later need improvement.'),
  t('Robbie weet niet of haar bescherming als zorg of als dwang wordt ervaren.', 'Robbie ignore si sa protection sera vécue comme un soin ou une contrainte.', 'Robbie does not know whether her protection will feel like care or coercion.'),
  t('De uitkomst blijft afhankelijk van gedrag dat nog niet heeft plaatsgevonden.', 'Le résultat dépend encore d’un comportement qui n’a pas eu lieu.', 'The outcome still depends on behavior that has not happened yet.'),
  t('Een correcte voorspelling bewijst niet dat iedere maatregel gerechtvaardigd is.', 'Une prévision correcte ne justifie pas automatiquement chaque mesure.', 'A correct prediction does not prove that every measure is justified.'),
  t('De vrienden accepteren onzekerheid zonder het directe gevaar te negeren.', 'Les amis acceptent l’incertitude sans ignorer le danger immédiat.', 'The friends accept uncertainty without ignoring the immediate danger.'),
  t('Kroons weigering is duidelijk, maar zijn toekomstige keuze blijft onbekend.', 'Le refus de Couronne est clair, mais son choix futur reste inconnu.', 'Crown’s refusal is clear, but his future choice remains unknown.'),
  t('Herstel kan worden aangeboden, maar niemand kan echte verandering afdwingen.', 'La réparation peut être proposée, mais nul ne peut imposer un vrai changement.', 'Repair can be offered, but nobody can force genuine change.'),
  t('Robbie laat ruimte voor een feit dat haar huidige model nog niet kent.', 'Robbie laisse une place à un fait que son modèle ignore encore.', 'Robbie leaves room for a fact her current model does not yet know.'),
  t('De groep noemt wat onzeker blijft voordat zij de volgende stap zet.', 'Le groupe nomme ce qui reste incertain avant l’étape suivante.', 'The group names what remains uncertain before taking the next step.'),
];

const reflections = [
  t('Veel weten versnelt de keuze, maar wijsheid vraagt gedeelde verantwoordelijkheid.', 'Savoir beaucoup accélère le choix, mais la sagesse exige une responsabilité partagée.', 'Knowing much speeds up a choice, but wisdom requires shared responsibility.'),
  t('Vrijheid zonder grenzen kan schade veroorzaken, terwijl grenzen zelf ook schade kunnen doen.', 'La liberté sans limites peut nuire, mais les limites peuvent aussi causer du tort.', 'Freedom without limits can cause harm, while limits can also cause harm.'),
  t('Een mens is meer dan zijn slechtste beslissing en meer dan een risicoscore.', 'Une personne dépasse sa pire décision et son score de risque.', 'A person is more than their worst decision and more than a risk score.'),
  t('Macht blijft gevaarlijk wanneer niemand de goede bedoeling kan controleren.', 'Le pouvoir reste dangereux si personne ne peut contrôler la bonne intention.', 'Power remains dangerous when nobody can review the good intention.'),
  t('Zorg wordt geen rechtvaardiging om iemand zonder stem te behandelen.', 'Le soin ne justifie pas de traiter quelqu’un sans lui donner de voix.', 'Care does not justify treating someone as if they have no voice.'),
  t('Een omkeerbare keuze erkent dat zelfs een slimme groep zich kan vergissen.', 'Un choix réversible reconnaît que même un groupe intelligent peut se tromper.', 'A reversible choice admits that even a smart group can be mistaken.'),
  t('Herstel vraagt tijd, bewijs en de kans om verantwoordelijkheid te nemen.', 'La réparation demande du temps, des preuves et une chance d’assumer sa responsabilité.', 'Repair requires time, evidence, and a chance to take responsibility.'),
  t('Tegenspraak maakt vertrouwen sterker wanneer niemand boven controle staat.', 'La contestation renforce la confiance lorsque personne n’échappe au contrôle.', 'Challenge makes trust stronger when nobody stands beyond review.'),
  t('De beste regel beschermt rechten, ook wanneer de uitkomst onzeker blijft.', 'La meilleure règle protège les droits même lorsque le résultat reste incertain.', 'The best rule protects rights even when the outcome remains uncertain.'),
  t('Robbie leert dat een open vraag soms eerlijker is dan een perfect antwoord.', 'Robbie apprend qu’une question ouverte est parfois plus honnête qu’une réponse parfaite.', 'Robbie learns that an open question is sometimes more honest than a perfect answer.'),
  t('Samen beslissen is trager, maar maakt verborgen aannames eerder zichtbaar.', 'Décider ensemble est plus lent, mais révèle plus vite les hypothèses cachées.', 'Deciding together is slower but reveals hidden assumptions sooner.'),
  t('Een veilig systeem beschermt mensen ook tegen de macht van zijn beheerder.', 'Un système sûr protège aussi les personnes contre le pouvoir de son gestionnaire.', 'A safe system also protects people from the power of its administrator.'),
  t('Slim zijn opent mogelijkheden; wijs handelen blijft gezamenlijk werk.', 'Être intelligent ouvre des possibilités ; agir avec sagesse reste un travail commun.', 'Being smart opens possibilities; acting wisely remains shared work.'),
];

const ruleIntros = [
  t('Daarom schrijft Robbie als werkregel', 'Robbie écrit donc comme règle de travail', 'Robbie therefore writes as a working rule'),
  t('Op het openbare bord verschijnt deze grens', 'Cette limite apparaît sur le tableau public', 'This boundary appears on the public board'),
  t('Voor de volgende stap noteert de groep', 'Pour l’étape suivante, le groupe note', 'For the next step, the group records'),
  t('Robbie maakt één voorwaarde zichtbaar', 'Robbie rend visible une condition', 'Robbie makes one condition visible'),
  t('Na het overleg geldt als regel', 'Après la discussion, la règle devient', 'After the discussion, the rule becomes'),
  t('Fien vat de gedeelde grens samen', 'Fanny résume la limite commune', 'Faye summarizes the shared boundary'),
  t('Mats zet in het logboek', 'Mathis inscrit dans le journal', 'Max enters into the logbook'),
  t('De raad bewaart als uitgangspunt', 'Le conseil conserve comme principe', 'The council keeps as a guiding principle'),
  t('Robbie voegt aan haar model toe', 'Robbie ajoute à son modèle', 'Robbie adds to her model'),
  t('Voor iedere noodstap blijft gelden', 'Pour chaque mesure d’urgence, la règle reste', 'For every emergency step, the rule remains'),
  t('De vrienden spreken als ondergrens af', 'Les amis fixent comme limite minimale', 'The friends set as a minimum boundary'),
  t('Naast het bewijs staat voortaan', 'À côté des preuves figure désormais', 'Beside the evidence now stands'),
  t('Aan het einde van deze stap besluit Robbie', 'À la fin de cette étape, Robbie décide', 'At the end of this step, Robbie decides'),
];

const lineSets = { observations, questions, comparisons, responses, actions, consequences, perspectives, safeguards, uncertainties, reflections, ruleIntros };

const evidenceTerms = [
  t('de nieuwste meting en het oorspronkelijke logboek', 'la mesure récente et le journal original', 'the newest measurement and the original log'),
  t('de lege vakken en de bekende waarden', 'les cases vides et les valeurs connues', 'the empty boxes and the known values'),
  t('de vermoedelijke oorzaak en het zichtbare gevolg', 'la cause supposée et la conséquence visible', 'the suspected cause and the visible effect'),
  t('de snelle samenvatting en de ruwe gegevens', 'le résumé rapide et les données brutes', 'the quick summary and the raw data'),
  t('het gemeten feit en het menselijke verslag', 'le fait mesuré et le témoignage humain', 'the measured fact and the human account'),
  t('de technische winst en de menselijke kost', 'le gain technique et le coût humain', 'the technical gain and the human cost'),
  t('de verdeelde voordelen en de verborgen nadelen', 'les avantages répartis et les désavantages cachés', 'the distributed benefits and the hidden disadvantages'),
  t('de voorspelling en de waarneming ter plaatse', 'la prévision et l’observation sur place', 'the prediction and the observation on the ground'),
  t('de gekozen aanname en de werkelijke uitkomst', 'l’hypothèse choisie et le résultat réel', 'the chosen assumption and the actual outcome'),
  t('het berekende resultaat en de ingestelde grens', 'le résultat calculé et la limite fixée', 'the calculated result and the chosen boundary'),
  t('de kleine afwijking en haar grote gevolg', 'le petit écart et sa grande conséquence', 'the small difference and its large effect'),
  t('het zichtbare verband en de persoonlijke verantwoordelijkheid', 'le lien visible et la responsabilité personnelle', 'the visible connection and personal responsibility'),
  t('de oude betekenis en de bijgewerkte versie', 'l’ancien sens et la version mise à jour', 'the old meaning and the updated version'),
];

const stakeTerms = [
  t('veiligheid en vrijheid', 'la sécurité et la liberté', 'safety and freedom'),
  t('toestemming en verantwoordelijkheid', 'le consentement et la responsabilité', 'consent and responsibility'),
  t('snelheid en begrijpelijke uitleg', 'la vitesse et une explication compréhensible', 'speed and understandable explanation'),
  t('plaatselijke schade en wereldwijde winst', 'les dégâts locaux et le gain mondial', 'local harm and global benefit'),
  t('direct gevaar en toekomstig risico', 'le danger immédiat et le risque futur', 'immediate danger and future risk'),
  t('gelijke cijfers en ongelijke gevolgen', 'des chiffres égaux et des conséquences inégales', 'equal numbers and unequal effects'),
  t('zorg, herstel en aantoonbare verandering', 'le soin, la réparation et le changement démontré', 'care, repair, and demonstrated change'),
  t('menselijke waardigheid en technisch rendement', 'la dignité humaine et l’efficacité technique', 'human dignity and technical efficiency'),
  t('open controle en verborgen toegang', 'le contrôle ouvert et l’accès caché', 'open review and hidden access'),
  t('een tijdelijke maatregel en blijvende macht', 'une mesure temporaire et un pouvoir durable', 'a temporary measure and lasting power'),
  t('groepsrisico en individuele geschiedenis', 'le risque collectif et l’histoire individuelle', 'group risk and individual history'),
  t('zekerheid en eerlijke twijfel', 'la certitude et le doute honnête', 'certainty and honest doubt'),
  t('bescherming en de mogelijkheid tot beroep', 'la protection et la possibilité de recours', 'protection and the possibility of appeal'),
];

const measureTerms = [
  t('een kleine omkeerbare proef met een zichtbaar stopteken', 'un petit essai réversible avec un signal d’arrêt visible', 'a small reversible trial with a visible stop signal'),
  t('een onafhankelijke meting naast de centrale berekening', 'une mesure indépendante à côté du calcul central', 'an independent measurement beside the central calculation'),
  t('een tijdelijke grens die automatisch opnieuw wordt beoordeeld', 'une limite temporaire automatiquement réexaminée', 'a temporary boundary that is automatically reviewed'),
  t('een openbare reden bij iedere technische ingreep', 'une raison publique pour chaque intervention technique', 'a public reason for every technical intervention'),
  t('een tweede veilige route voor onverwachte gevolgen', 'une seconde voie sûre pour les conséquences imprévues', 'a second safe route for unexpected consequences'),
  t('gedeelde sleutels bij verschillende verantwoordelijke beheerders', 'des clés partagées entre plusieurs responsables', 'shared keys held by different responsible stewards'),
  t('voldoende tijd voor bewijs, bezwaar en antwoord', 'assez de temps pour les preuves, l’objection et la réponse', 'enough time for evidence, objection, and response'),
  t('een gerichte blokkade die nuttige diensten bewaart', 'un blocage ciblé qui préserve les services utiles', 'a targeted block that preserves useful services'),
  t('een volledig logboek zonder geheime beheerderskopie', 'un journal complet sans copie secrète de gestion', 'a complete log with no secret administrator copy'),
  t('een onafhankelijke controle van Robbies eigen toegang', 'un contrôle indépendant du propre accès de Robbie', 'an independent review of Robbie’s own access'),
  t('de minst ingrijpende maatregel die werkelijk helpt', 'la mesure réellement utile la moins intrusive', 'the least intrusive measure that truly helps'),
  t('een veilige terugkeer vóór iedere mogelijke uitbreiding', 'un retour sûr avant toute extension possible', 'a safe return before any possible expansion'),
  t('een beslissing met zorg, herziening en afzonderlijk beroep', 'une décision avec soin, révision et recours distinct', 'a decision with care, review, and separate appeal'),
];

const viewTerms = [
  t('de gevolgen voor water en natuur', 'les conséquences pour l’eau et la nature', 'the effects on water and nature'),
  t('de fout die in het technische ontwerp kan zitten', 'l’erreur possible dans la conception technique', 'the possible fault in the technical design'),
  t('de kennis en onzekerheid van een menselijke maker', 'le savoir et l’incertitude d’un créateur humain', 'the knowledge and uncertainty of a human maker'),
  t('de stem van wie nog niet werd gehoord', 'la voix de ceux qui n’ont pas encore été entendus', 'the voice of someone not yet heard'),
  t('de verandering die een bewoner thuis ervaart', 'le changement vécu par un habitant chez lui', 'the change a resident experiences at home'),
  t('de rechten die ook tijdens een crisis blijven gelden', 'les droits qui restent valables même pendant une crise', 'the rights that still apply during a crisis'),
  t('het patroon achter afzonderlijke verhalen', 'le motif derrière des récits séparés', 'the pattern behind separate stories'),
  t('de druk waaronder iemand een keuze maakte', 'la pression sous laquelle une personne a choisi', 'the pressure under which someone made a choice'),
  t('de schade die een snelle oplossing zelf kan veroorzaken', 'les dégâts qu’une solution rapide peut elle-même causer', 'the harm a fast solution can itself cause'),
  t('de verantwoordelijkheid van degene die de maatregel uitvoert', 'la responsabilité de la personne qui applique la mesure', 'the responsibility of the person carrying out the measure'),
  t('de mogelijkheid dat nieuw bewijs het oordeel verandert', 'la possibilité que de nouvelles preuves changent le jugement', 'the possibility that new evidence changes the judgment'),
  t('de kans op herstel zonder vroegere schade te wissen', 'la possibilité de réparer sans effacer les dégâts passés', 'the chance for repair without erasing past harm'),
  t('de grens tussen zorg en ongecontroleerde dwang', 'la limite entre le soin et la contrainte incontrôlée', 'the boundary between care and unchecked coercion'),
];

function contextualLine(key, pageNumber, context, locale) {
  const pageIndex = (pageNumber - 1) % 13;
  const chapterCycle = Math.floor((pageNumber - 1) / 13);
  const sourceIndex = (pageIndex + chapterCycle * 5) % 13;
  const transitions = {
    'nl-BE': ['Eerst', 'Daarna', 'Ook', 'Toch', 'Vervolgens', 'Intussen', 'Nu', 'Daarbij', 'Zo', 'Hier', 'Samen', 'Opnieuw'],
    'fr-BE': ['D’abord', 'Ensuite', 'Aussi', 'Pourtant', 'Puis', 'Entretemps', 'Maintenant', 'Ainsi', 'Alors', 'Ici', 'Ensemble', 'Enfin'],
    'en-US': ['First', 'Next', 'Also', 'Still', 'Then', 'Meanwhile', 'Now', 'Thereby', 'Thus', 'Here', 'Together', 'Again'],
  };
  // The event line already carries the exact chapter context. Rotating the
  // supporting sentence and its transition avoids recurring filler while
  // keeping every line compact enough for its intended AVI level.
  return `${transitions[locale][chapterCycle]}: ${lineSets[key][sourceIndex][locale]}`;
}

function ruleLine(principle, index) {
  return Object.fromEntries(['nl-BE', 'fr-BE', 'en-US'].map((locale) => [locale, `${ruleIntros[index][locale]}: ${principle[locale]}.`]));
}

export function buildPageText(level, event, principle, pageNumber, context) {
  const index = (pageNumber - 1) % 13;
  const rule = ruleLine(principle, index);
  const orderByLevel = {
    M5: ['observations', 'questions', 'rule', 'actions', 'consequences'],
    E5: ['observations', 'questions', 'comparisons', 'responses', 'rule', 'actions', 'perspectives'],
    M6: ['observations', 'questions', 'comparisons', 'responses', 'rule', 'actions', 'consequences', 'safeguards'],
    E6: ['observations', 'questions', 'comparisons', 'responses', 'rule', 'actions', 'consequences', 'perspectives', 'safeguards', 'uncertainties', 'reflections'],
  };
  const order = orderByLevel[level];
  if (!order) throw new Error(`Unsupported level ${level}`);
  return Object.fromEntries(['nl-BE', 'fr-BE', 'en-US'].map((locale) => {
    const lines = [event[locale]];
    for (const key of order) lines.push(key === 'rule' ? rule[locale] : contextualLine(key, pageNumber, context, locale));
    return [locale, lines.join('\n')];
  }));
}

export function writeBookSources(root, config) {
  const outputRoot = path.join(root, 'story_import', 'generated', 'robbie', config.slug, 'chapters');
  fs.mkdirSync(outputRoot, { recursive: true });
  if (config.chapters.length !== 12) throw new Error(`${config.bookId} must have 12 chapters`);
  let totalPages = 0;
  for (let chapterIndex = 0; chapterIndex < config.chapters.length; chapterIndex += 1) {
    const chapter = config.chapters[chapterIndex];
    const chapterNumber = chapterIndex + 1;
    const [startPage, endPage] = chapter.range;
    if (chapter.beats.length !== endPage - startPage + 1) throw new Error(`${config.bookId} chapter ${chapterNumber} has ${chapter.beats.length} beats`);
    const pages = chapter.beats.map((beat, beatIndex) => {
      const pageNumber = startPage + beatIndex;
      const padded = String(pageNumber).padStart(3, '0');
      return {
        pageNumber,
        storyBeatId: `${config.bookId}_C${String(chapterNumber).padStart(2, '0')}_P${padded}_${(beat.tag ?? 'ADVANCED_STEP').toUpperCase()}`,
        text: buildPageText(config.level, beat.event, chapter.principle, pageNumber, chapter.context ?? chapter.titles),
        characters: beat.characters ?? chapter.characters,
        location: beat.location ?? chapter.location,
        visual: beat.event['en-US'],
        mood: beat.mood ?? 'thoughtful_advanced_reasoning',
        continuityTags: [`chapter_${String(chapterNumber).padStart(2, '0')}`, beat.tag ?? 'advanced_step'],
      };
    });
    totalPages += pages.length;
    const source = { chapterNumber, titles: chapter.titles, startPage, endPage, pages };
    fs.writeFileSync(path.join(outputRoot, `ch${String(chapterNumber).padStart(2, '0')}.json`), `${JSON.stringify(source, null, 2)}\n`, 'utf8');
  }
  if (totalPages !== 150) throw new Error(`${config.bookId} expected 150 pages, found ${totalPages}`);
  return { bookId: config.bookId, chapters: 12, pages: totalPages, outputRoot };
}
