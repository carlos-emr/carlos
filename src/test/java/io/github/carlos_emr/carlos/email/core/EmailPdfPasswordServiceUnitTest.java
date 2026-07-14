/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("security")
@DisplayName("EmailPdfPasswordService")
class EmailPdfPasswordServiceUnitTest {
    private static final String PATIENT_UNFRIENDLY_REVIEW_WORDS = """
            abdomen abdominal abnormal abrasion aching anemia anemic backache bacteria bacterium bladder bruising
            capillary cavity coma cough cramp dental dentist denture disinfect dizzy dosage earache epidemic
            epilepsy epileptic fever fracture germicide germinate germless germproof glandular glaucoma headache
            liver mammogram pancreas paralyses paralysis paralyze patient pelvis poison pregnancy pregnant pulse
            rash reflux scurvy spleen sprain sterile surgery symptom uninjured unsterile virus wound zit ambush
            arson backstab battle browbeat cannon chokehold cruelly cruelness cruelty crushable crushed crusher
            crushing danger daredevil disaster drown embattled endanger entrap explode handgun harmful hate hunter
            hunting huntress huntsman jackknife manhunt overkill penknife punch slapping strangle threaten trapped
            trapper trapping traps undead wreckage wrecker wrecking groin kissable kisser kissing unisexual absinthe
            brewery cannabis nicotine opium smoked smokeless smokiness smoking smoky absurd afflicted afraid bleak
            boring broken bully careless clumsy crazy decay dreadful evil frighten gloomy greedy lazy obnoxious panic
            prison revenge rotten scary shame sloppy trash unfair unlucky unwanted villain bishop hulk joker rocket spawn
            storm superman vision vulture
            abuse addict addiction alcohol allergic allergy ambulance anatomy anger anxious attack autopsy blood body
            bomb cancer clinic corpse crime death disease divorce doctor drug fever gout gun harm hurt illness injury
            jail knife medic medicine murder pain phobia prognosis refugee relapse remission sick steroid stress surgery
            syringe trauma vaccine violence weapon wicked wine wound
            abortion accident artillery bandage chlamydia crossfire doctorate hearse maggot scalpel urinal weaponry
            bitch booze bourbon brandy brewery brothel bullet burial tampon tavern tequila thigh thong throat tomb
            tombstone tomahawk torpedo trap trash urinal vermin vibrator vomit warhead warship
            afghan arabic black clan race tribe
            acoustic acoustics aged ale arrowhead artichoke ash ashes ashtray axis baboon back backbone backdoor
            backside bacon baggage bar bastard battalion battery beat beater bedroom bedside beef begging belt belting
            beltway bible bikini binder birth bite blackjack blind blindfold blob blockage blouse blow blower
            bosom bottom bound brake brat break breaker breakfast breath bridle brigade brittle broadcast brokerage
            brownie browser bud buff buffet bulge bush bypass byproduct cage calves camcorder camel cane canine
            canon carpet carving casino casket cast casting cataract catfish catheter cell cemetery chain
            chainsaw champagne chase chaser cheek cheetah chimp chin chip choke chop chopper choppers cider cinder
            cleaver cleft cliff closet clothes clothing cock cockpit cockroach cocktail codeine cognac coke collision
            color colors coloring labor neighbor center theater meter fiber gray mold molding pajamas jewelry
            plow check
            gin martini mead pub rum saloon spirits stout winery card chips deal dealer deck dice die lottery poker
            prize roulette slot token arrow axe axes baton blaze bow dart darts fighter hit mace missile scuffle shank
            shoot shooter slap slingshot spear spike strike sword tackle target trigger wrench bang bun
            cherry cream cucumber dildo hookup hotdog hump knob nut pants peach pussy pussycat rod rubber
            sausage screw sex shaft strip stripper thrust tube wiener zipper
            skyrocket altar amulet cathedral chalice chapel convent cross halo idol monastery mosque
            pagoda pastor pew preaching pulpit rabbi reverend scripture shrine synagogue talisman temple totem urn zodiac
            admiral bunker catapult cavalry corps crossbow dynamite execution fort fortress infantry javelin minefield
            mines mortar navy patrol police precinct regiment riot saber silencer soldier spearhead squadron trench trident
            turret calf chest ear elbow face gut guts hair hearing human implant incubator mouth mouthful navel neck needle
            needles ointment pharmacy pore rib shin shingles shoulder skeleton spine tablet teeth thumb toe tooth waist welt
            ace bubbly crack date doggy drawers eggplant hunk pickle racehorse racetrack raceway scotch sherry
            solitaire spade spades swimsuit swimwear tonic vineyard abyss bash collapse crush dark derelict escape ghost
            goodbye hazard inferno mildew parasite pest predator prey reaper roach scavenger sewer shark shipwreck shock
            shroud siren slum waste wasteland warning brazil kimono roman siamese viceroy
            cobra fang flea gator grizzly
            mosquito rat serpent
            bologna brisket burger caviar filet fillet ham jerky kebab
            meatball meatloaf mussel pepperoni pork poultry prawn salami
            steak veal digest diet gel gluten gill gills knuckles
            membrane menthol mould nappy piles pill plaster pressure puncture razor retainer scale scales
            shaving tights waistband weight wristband bait barb bats batter bout boxer broadside buck
            bull cheers club corkscrew hunt kick laser lasso maul mine ninja pike pitchfork
            popper pusher racket rip rumble sack shard sharp shear shears slash smack smash snare talon throttle
            torch whip whipping angus auk axolotl cygnet edifice facsimile gnu ibex kudu oryx pueblo serval stoat vicuna
            xerox congress governor olympics senate state union venus vote
            bathrobe bathroom bathtub bed bedding earpiece fur hairpin lap lapping lavatory rear
            rump shower sole soles tail touch touching washroom young youth grub larva larvae mite moulding spore worm
            elixir ether hash mate mount mounting pant panting tartar campaign capital capitol emperor empire evidence
            fall fire fireball firehouse fireworks flood gauntlet guard hurricane jury kingpin
            lightning royalty shielding squad testimony throne tornado trial vagabond
            ipod lego polaroid skittles twitter velcro
            exam lab sample scan scanner screening test tester marrow mole mask rodent cot dormitory
            lodgings suite vacancy boom burst ditch drain drill drop end force freeze fuse mess
            obstacle retreat rust saw scissors shovel snag stain stinger submarine carol manger sanctuary flavoring harbor
            abode apparatus apronstring aqueduct backwater baritone bayou bazaar biosphere bonanza borough bureau
            cache chateau chevron citadel coliseum conch conduit decal diffuser dubbing emporium equinox facade filament
            hacienda hoarding hyacinth inkwell itinerary juncture khaki kiln meridian mesquite oblong
            regalia schematic tributary vestibule withers nymph
            """;

    @Test
    @DisplayName("should generate seven lowercase hyphen-separated words")
    void shouldGenerateSevenLowercaseHyphenSeparatedWords() {
        EmailPdfPasswordService service = new EmailPdfPasswordService();

        String passphrase = service.generatePassphrase();

        assertThat(passphrase).matches("^[a-z]+(-[a-z]+){6}$");
        assertThat(passphrase.split("-")).hasSize(7);
    }

    @Test
    @DisplayName("should use secure random indexes to select words")
    void shouldUseSecureRandomIndexesToSelectWords() {
        EmailPdfPasswordService service = new EmailPdfPasswordService(testWords(4096), new FixedSecureRandom(0, 1, 2, 3, 4, 5, 6));

        String passphrase = service.generatePassphrase();

        assertThat(passphrase).isEqualTo("worda-wordb-wordc-wordd-worde-wordf-wordg");
    }

    @Test
    @DisplayName("should load a 4096 word resource wordlist with 84 bits of entropy")
    void shouldLoadLargeEnoughResourceWordlistWithExpectedEntropy() {
        EmailPdfPasswordService service = new EmailPdfPasswordService();

        assertThat(service.getWordListSize()).isEqualTo(4096);
        assertThat(service.getEntropyBits()).isEqualTo(84.0);
    }

    @Test
    @DisplayName("should not include patient-facing sensitive review words")
    void shouldNotIncludePatientFacingSensitiveReviewWords() throws Exception {
        String[] blockedWords = PATIENT_UNFRIENDLY_REVIEW_WORDS.trim().split("\\s+");

        assertThat(resourceWords()).doesNotContain(blockedWords);
    }

    @Test
    @DisplayName("should calculate entropy from wordlist size and word count")
    void shouldCalculateEntropyFromWordlistSizeAndWordCount() {
        double entropy = EmailPdfPasswordService.calculateEntropyBits(4096, 7);

        assertThat(entropy).isEqualTo(84.0);
    }

    @Test
    @DisplayName("should reject a wordlist below the minimum size")
    void shouldRejectWordlistBelowMinimumSize() {
        assertThatThrownBy(() -> new EmailPdfPasswordService(testWords(4095), new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 4096");
    }

    @Test
    @DisplayName("should reject non-lowercase ASCII words")
    void shouldRejectInvalidWords() {
        List<String> words = testWords(4096);
        words.set(100, "two-words");

        assertThatThrownBy(() -> new EmailPdfPasswordService(words, new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid word");
    }

    @Test
    @DisplayName("should reject duplicate words")
    void shouldRejectDuplicateWords() {
        List<String> words = testWords(4096);
        words.set(100, words.get(99));

        assertThatThrownBy(() -> new EmailPdfPasswordService(words, new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate word");
    }

    private static List<String> testWords(int count) {
        List<String> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            words.add("word" + toLetters(i));
        }
        return words;
    }

    private static String toLetters(int value) {
        StringBuilder builder = new StringBuilder();
        do {
            builder.append((char) ('a' + (value % 26)));
            value = value / 26;
        } while (value > 0);
        return builder.toString();
    }

    private static List<String> resourceWords() throws Exception {
        InputStream stream = EmailPdfPasswordService.class.getResourceAsStream(EmailPdfPasswordService.WORDLIST_RESOURCE);
        assertThat(stream).isNotNull();

        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmedLine.split("\\s+");
                words.add(parts[parts.length - 1]);
            }
        }
        return words;
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private final int[] values;
        private int index;

        private FixedSecureRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            if (values.length == 0) {
                return 0;
            }
            int value = values[index % values.length];
            index++;
            return value % bound;
        }
    }
}
