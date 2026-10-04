package yul.landing

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// GENERATED from tools/families.txt: one photo per aircraft family, matched by exact ICAO type code.
// Types not listed here fall back to a photo chosen from the transponder category.

private class PhotoFamily(@DrawableRes val res: Int, val codes: String)

private val PHOTO_FAMILIES = listOf(
    PhotoFamily(R.drawable.photo_a220, "BCS1 BCS3"),
    PhotoFamily(R.drawable.photo_a300, "A306 A30B"),
    PhotoFamily(R.drawable.photo_a310, "A310"),
    PhotoFamily(R.drawable.photo_a3st, "A3ST A337"),
    PhotoFamily(R.drawable.photo_a319, "A318 A319 A19N"),
    PhotoFamily(R.drawable.photo_a320, "A320 A20N"),
    PhotoFamily(R.drawable.photo_a321, "A321 A21N"),
    PhotoFamily(R.drawable.photo_a330, "A332 A333 A338 A339"),
    PhotoFamily(R.drawable.photo_a340, "A342 A343 A345 A346"),
    PhotoFamily(R.drawable.photo_a350, "A359 A35K"),
    PhotoFamily(R.drawable.photo_a380, "A388"),
    PhotoFamily(R.drawable.photo_a400m, "A400"),
    PhotoFamily(R.drawable.photo_b707, "B701 B703 B720"),
    PhotoFamily(R.drawable.photo_b727, "B721 B722"),
    PhotoFamily(R.drawable.photo_b737c, "B732 B733 B734 B735"),
    PhotoFamily(R.drawable.photo_b737, "B736 B737 B738 B739"),
    PhotoFamily(R.drawable.photo_b737max, "B37M B38M B39M B3XM"),
    PhotoFamily(R.drawable.photo_b717, "B712"),
    PhotoFamily(R.drawable.photo_b747, "B741 B742 B743 B744 B74R B74S"),
    PhotoFamily(R.drawable.photo_b748, "B748"),
    PhotoFamily(R.drawable.photo_b757, "B752 B753"),
    PhotoFamily(R.drawable.photo_b767, "B762 B763 B764"),
    PhotoFamily(R.drawable.photo_b777, "B772 B773 B77L B77W"),
    PhotoFamily(R.drawable.photo_b777x, "B778 B779"),
    PhotoFamily(R.drawable.photo_b787, "B788 B789 B78X"),
    PhotoFamily(R.drawable.photo_md80, "MD81 MD82 MD83 MD87 MD88"),
    PhotoFamily(R.drawable.photo_md90, "MD90"),
    PhotoFamily(R.drawable.photo_dc9, "DC91 DC92 DC93 DC94 DC95"),
    PhotoFamily(R.drawable.photo_dc8, "DC85 DC86 DC87"),
    PhotoFamily(R.drawable.photo_md11, "MD11"),
    PhotoFamily(R.drawable.photo_dc10, "DC10"),
    PhotoFamily(R.drawable.photo_l1011, "L101"),
    PhotoFamily(R.drawable.photo_bac111, "BA11"),
    PhotoFamily(R.drawable.photo_b146, "B461 B462 B463"),
    PhotoFamily(R.drawable.photo_avrorj, "RJ70 RJ85 RJ1H"),
    PhotoFamily(R.drawable.photo_fokkerjet, "F100 F70 F28"),
    PhotoFamily(R.drawable.photo_fokkerprop, "F27 F50 F60"),
    PhotoFamily(R.drawable.photo_crj200, "CRJ1 CRJ2"),
    PhotoFamily(R.drawable.photo_crj, "CRJ7 CRJ9 CRJX"),
    PhotoFamily(R.drawable.photo_erj, "E135 E145 E45X E35L"),
    PhotoFamily(R.drawable.photo_ejet, "E170 E175 E75L E75S"),
    PhotoFamily(R.drawable.photo_e190, "E190 E195"),
    PhotoFamily(R.drawable.photo_e2, "E275 E290 E295"),
    PhotoFamily(R.drawable.photo_ssj, "SU95"),
    PhotoFamily(R.drawable.photo_c919, "C919"),
    PhotoFamily(R.drawable.photo_mrj, "MRJ7 MRJ9"),
    PhotoFamily(R.drawable.photo_tu154, "T154 T134 T204"),
    PhotoFamily(R.drawable.photo_tubomber, "TU95 TU16 TU22 T160 T22M"),
    PhotoFamily(R.drawable.photo_il76, "IL76"),
    PhotoFamily(R.drawable.photo_ilairliner, "IL62 IL86 IL96 IL18 IL14 IL38 IL28"),
    PhotoFamily(R.drawable.photo_an124, "A124 A225"),
    PhotoFamily(R.drawable.photo_antransport, "AN12 AN22 AN8 AN70 AN72"),
    PhotoFamily(R.drawable.photo_an24, "AN24 AN26 AN30 AN32 AN38"),
    PhotoFamily(R.drawable.photo_an2, "AN2"),
    PhotoFamily(R.drawable.photo_yak42, "YK42 YK40"),
    PhotoFamily(R.drawable.photo_atr42, "AT43 AT44 AT45 AT46"),
    PhotoFamily(R.drawable.photo_atr, "AT72 AT73 AT75 AT76"),
    PhotoFamily(R.drawable.photo_dash8c, "DH8A DH8B DH8C"),
    PhotoFamily(R.drawable.photo_dash8, "DH8D"),
    PhotoFamily(R.drawable.photo_saab340, "SF34"),
    PhotoFamily(R.drawable.photo_saab2000, "SB20"),
    PhotoFamily(R.drawable.photo_js41, "JS31 JS32 JS41"),
    PhotoFamily(R.drawable.photo_b1900, "B190"),
    PhotoFamily(R.drawable.photo_kingair, "BE9L BE9T BE10 BE20 BE30 B350 B360 BE99"),
    PhotoFamily(R.drawable.photo_dornier328, "D328 J328"),
    PhotoFamily(R.drawable.photo_do228, "D228"),
    PhotoFamily(R.drawable.photo_do27, "DO27 DO28"),
    PhotoFamily(R.drawable.photo_e120, "E120"),
    PhotoFamily(R.drawable.photo_e110, "E110"),
    PhotoFamily(R.drawable.photo_metro, "SW3 SW4"),
    PhotoFamily(R.drawable.photo_pc12, "PC12"),
    PhotoFamily(R.drawable.photo_pc6, "PC6P PC6T"),
    PhotoFamily(R.drawable.photo_tbm, "TBM TBM7 TBM8 TBM9"),
    PhotoFamily(R.drawable.photo_c208, "C208"),
    PhotoFamily(R.drawable.photo_conquest, "C441 C425"),
    PhotoFamily(R.drawable.photo_dhc6, "DHC6"),
    PhotoFamily(R.drawable.photo_dhc7, "DHC7"),
    PhotoFamily(R.drawable.photo_dhc2, "DHC2 DHC3"),
    PhotoFamily(R.drawable.photo_dhc1, "DHC1"),
    PhotoFamily(R.drawable.photo_dhc4, "DHC4 DHC5"),
    PhotoFamily(R.drawable.photo_shorts, "SH33 SH36"),
    PhotoFamily(R.drawable.photo_skyvan, "SC7"),
    PhotoFamily(R.drawable.photo_l410, "L410"),
    PhotoFamily(R.drawable.photo_y12, "Y12 Y12F Y11"),
    PhotoFamily(R.drawable.photo_ma60, "MA60"),
    PhotoFamily(R.drawable.photo_cn235, "CN35"),
    PhotoFamily(R.drawable.photo_c212, "C212"),
    PhotoFamily(R.drawable.photo_c295, "C295"),
    PhotoFamily(R.drawable.photo_c27j, "C27J"),
    PhotoFamily(R.drawable.photo_p180, "P180"),
    PhotoFamily(R.drawable.photo_islander, "BN2P BN2T"),
    PhotoFamily(R.drawable.photo_skymaster, "C337 P337"),
    PhotoFamily(R.drawable.photo_cessnatwin, "C310 C320 C340 C402 C404 C414 C421 C303 C335 C336"),
    PhotoFamily(R.drawable.photo_citcj, "C525 C25A C25B C25C C25M C526"),
    PhotoFamily(R.drawable.photo_citmustang, "C510"),
    PhotoFamily(R.drawable.photo_citation, "C500 C501 C550 C551 C55B C560 C56X"),
    PhotoFamily(R.drawable.photo_citationx, "C650 C680 C68A C700 C750"),
    PhotoFamily(R.drawable.photo_challenger, "CL30 CL35 CL60"),
    PhotoFamily(R.drawable.photo_global, "GLEX GL5T GL7T"),
    PhotoFamily(R.drawable.photo_tutor, "CL41"),
    PhotoFamily(R.drawable.photo_gulfstream, "GLF2 GLF3 GLF4 GLF5 GLF6 G280 G150 G200 G800 G850"),
    PhotoFamily(R.drawable.photo_g159, "G159"),
    PhotoFamily(R.drawable.photo_falcon, "FA10 FA20 FA50 F900 F2TH FA7X FA8X FA6X FA62"),
    PhotoFamily(R.drawable.photo_learjet, "LJ23 LJ24 LJ25 LJ28 LJ31 LJ35 LJ40 LJ45 LJ55 LJ60 LJ70 LJ75 LJ85"),
    PhotoFamily(R.drawable.photo_hawker, "H25A H25B H25C BE40"),
    PhotoFamily(R.drawable.photo_premier, "PRM1"),
    PhotoFamily(R.drawable.photo_hondajet, "HDJT"),
    PhotoFamily(R.drawable.photo_phenom, "E50P E55P"),
    PhotoFamily(R.drawable.photo_praetor, "E545 E550"),
    PhotoFamily(R.drawable.photo_vision, "SF50"),
    PhotoFamily(R.drawable.photo_eclipse, "EA50"),
    PhotoFamily(R.drawable.photo_astra, "ASTR"),
    PhotoFamily(R.drawable.photo_sj30, "SJ30"),
    PhotoFamily(R.drawable.photo_pc24, "PC24"),
    PhotoFamily(R.drawable.photo_sabreliner, "SBR1 SBR2"),
    PhotoFamily(R.drawable.photo_c172, "C172 C175 C177 C72R C77R"),
    PhotoFamily(R.drawable.photo_c150, "C150 C152"),
    PhotoFamily(R.drawable.photo_c182, "C180 C182 C185 C188"),
    PhotoFamily(R.drawable.photo_c206, "C205 C206 C207 C210 C21T P210 T206 T210"),
    PhotoFamily(R.drawable.photo_c170, "C120 C140 C170 C195"),
    PhotoFamily(R.drawable.photo_c162, "C162"),
    PhotoFamily(R.drawable.photo_piper28, "P28A P28B P28R P28S P28T P28U"),
    PhotoFamily(R.drawable.photo_pa32, "PA32"),
    PhotoFamily(R.drawable.photo_pa46, "PA46 P46T"),
    PhotoFamily(R.drawable.photo_cub, "J3 PA18 PA11 PA12 PA14 PA15 PA16 PA17 PA20"),
    PhotoFamily(R.drawable.photo_pa24, "PA24 PA22"),
    PhotoFamily(R.drawable.photo_pa38, "PA38"),
    PhotoFamily(R.drawable.photo_pa25, "PA25"),
    PhotoFamily(R.drawable.photo_pa44, "PA44"),
    PhotoFamily(R.drawable.photo_pa34, "PA34"),
    PhotoFamily(R.drawable.photo_pa31, "PA31 PA30 PA27 PA23 PA60"),
    PhotoFamily(R.drawable.photo_cheyenne, "PAY1 PAY2 PAY3 PAY4"),
    PhotoFamily(R.drawable.photo_bonanza, "BE33 BE35 BE36"),
    PhotoFamily(R.drawable.photo_baron, "BE50 B58T BE55 BE56 BE58 BE60 BE65 BE95 BE76"),
    PhotoFamily(R.drawable.photo_musketeer, "BE19 BE23 BE24"),
    PhotoFamily(R.drawable.photo_staggerwing, "BE17 BE18"),
    PhotoFamily(R.drawable.photo_cirrus, "SR20 SR22"),
    PhotoFamily(R.drawable.photo_diamond, "DA20 DV20 DA40 DA50"),
    PhotoFamily(R.drawable.photo_da42, "DA42 DA62"),
    PhotoFamily(R.drawable.photo_mooney, "M20P M20T M20"),
    PhotoFamily(R.drawable.photo_grumman, "AA1 AA5"),
    PhotoFamily(R.drawable.photo_grob, "G115 G120 G109"),
    PhotoFamily(R.drawable.photo_lancair, "LNC2 LNC4 LNCE"),
    PhotoFamily(R.drawable.photo_rv, "RV3 RV4 RV6 RV7 RV8 RV9 RV10 RV12 RV14 RV4T"),
    PhotoFamily(R.drawable.photo_extra, "E300"),
    PhotoFamily(R.drawable.photo_pitts, "S1 S2P S2T"),
    PhotoFamily(R.drawable.photo_stearman, "ST75"),
    PhotoFamily(R.drawable.photo_maule, "M4 M5 M6 M7"),
    PhotoFamily(R.drawable.photo_ercoupe, "ERCO"),
    PhotoFamily(R.drawable.photo_colombia, "COL3 COL4 CORV"),
    PhotoFamily(R.drawable.photo_robin, "DR40 DR10 DR22"),
    PhotoFamily(R.drawable.photo_zlin, "Z42 Z43 Z50 Z37"),
    PhotoFamily(R.drawable.photo_yak52, "YK52 YK18 YK50 YK54 YK55"),
    PhotoFamily(R.drawable.photo_p51, "P51"),
    PhotoFamily(R.drawable.photo_p38, "P38"),
    PhotoFamily(R.drawable.photo_p40, "P40"),
    PhotoFamily(R.drawable.photo_p47, "P47"),
    PhotoFamily(R.drawable.photo_spitfire, "SPIT"),
    PhotoFamily(R.drawable.photo_hurricane, "HURI"),
    PhotoFamily(R.drawable.photo_mosquito, "MOSQ"),
    PhotoFamily(R.drawable.photo_b17, "B17"),
    PhotoFamily(R.drawable.photo_b24, "B24"),
    PhotoFamily(R.drawable.photo_b25, "B25"),
    PhotoFamily(R.drawable.photo_b29, "B29"),
    PhotoFamily(R.drawable.photo_dc3, "DC3 DC3S DC3T"),
    PhotoFamily(R.drawable.photo_c46, "C46"),
    PhotoFamily(R.drawable.photo_dc4, "DC4 DC6 DC7"),
    PhotoFamily(R.drawable.photo_connie, "CONI"),
    PhotoFamily(R.drawable.photo_l188, "L188"),
    PhotoFamily(R.drawable.photo_ju52, "JU52"),
    PhotoFamily(R.drawable.photo_t6, "T6"),
    PhotoFamily(R.drawable.photo_t28, "T28"),
    PhotoFamily(R.drawable.photo_t34, "T34P T34T"),
    PhotoFamily(R.drawable.photo_t38, "T38 T37"),
    PhotoFamily(R.drawable.photo_l39, "L39"),
    PhotoFamily(R.drawable.photo_t33, "T33"),
    PhotoFamily(R.drawable.photo_pilatustrainer, "PC7 PC9 PC21"),
    PhotoFamily(R.drawable.photo_tucano, "TUCA TUCR"),
    PhotoFamily(R.drawable.photo_mig15, "MG15 MG17 MG19"),
    PhotoFamily(R.drawable.photo_mig21, "MG21 MG23 MG25 MG29 MG31"),
    PhotoFamily(R.drawable.photo_sukhoi, "SU27 SU29 SU31 SU25 SU24 SU17 SU57"),
    PhotoFamily(R.drawable.photo_f16, "F16 F16X"),
    PhotoFamily(R.drawable.photo_f15, "F15"),
    PhotoFamily(R.drawable.photo_f18, "F18H F18S"),
    PhotoFamily(R.drawable.photo_f22, "F22"),
    PhotoFamily(R.drawable.photo_f35, "F35"),
    PhotoFamily(R.drawable.photo_f14, "F14"),
    PhotoFamily(R.drawable.photo_f4, "F4"),
    PhotoFamily(R.drawable.photo_f5, "F5"),
    PhotoFamily(R.drawable.photo_f104, "F104"),
    PhotoFamily(R.drawable.photo_f111, "F111"),
    PhotoFamily(R.drawable.photo_f117, "F117"),
    PhotoFamily(R.drawable.photo_f86, "F86"),
    PhotoFamily(R.drawable.photo_a10, "A10"),
    PhotoFamily(R.drawable.photo_u2, "U2"),
    PhotoFamily(R.drawable.photo_b2, "B2"),
    PhotoFamily(R.drawable.photo_b1, "B1"),
    PhotoFamily(R.drawable.photo_b52, "B52"),
    PhotoFamily(R.drawable.photo_e3, "E3CF E3TF"),
    PhotoFamily(R.drawable.photo_c17, "C17"),
    PhotoFamily(R.drawable.photo_c5, "C5M"),
    PhotoFamily(R.drawable.photo_c141, "C141"),
    PhotoFamily(R.drawable.photo_c130, "C130 C30J"),
    PhotoFamily(R.drawable.photo_kc135, "C135 K35R K35E"),
    PhotoFamily(R.drawable.photo_eurofighter, "EUFI"),
    PhotoFamily(R.drawable.photo_tornado, "TOR"),
    PhotoFamily(R.drawable.photo_rafale, "RFAL"),
    PhotoFamily(R.drawable.photo_mirage, "MIR2 MIRA"),
    PhotoFamily(R.drawable.photo_harrier, "HAR"),
    PhotoFamily(R.drawable.photo_hawkjet, "HAWK"),
    PhotoFamily(R.drawable.photo_alphajet, "AJET"),
    PhotoFamily(R.drawable.photo_jaguar, "JAGR"),
    PhotoFamily(R.drawable.photo_airtractor, "AT2P AT3P AT3T AT5P AT5T AT6T AT8T"),
    PhotoFamily(R.drawable.photo_aerocommander, "AC50 AC52 AC56 AC68 AC6L AC72 AC80 AC90 AC95"),
    PhotoFamily(R.drawable.photo_citabria, "CH7A CH7B CH80"),
    PhotoFamily(R.drawable.photo_tigermoth, "DH82 DH60 DH80"),
    PhotoFamily(R.drawable.photo_dragonrapide, "DH89 DH84 DH83 DH85 DH87 DH90"),
    PhotoFamily(R.drawable.photo_falke, "SF25 SF28 SF24"),
    PhotoFamily(R.drawable.photo_gippsaero, "GA8 GA8C"),
    PhotoFamily(R.drawable.photo_sf260, "F260"),
    PhotoFamily(R.drawable.photo_l5, "L5"),
)

private val PHOTO_BY_TYPE: Map<String, Int> by lazy {
    val m = HashMap<String, Int>(1024)
    for (f in PHOTO_FAMILIES) for (code in f.codes.split(' ')) if (code !in m) m[code] = f.res
    m
}

@DrawableRes
private fun aircraftPhotoRes(typeCode: String?, category: Int): Int {
    val code = typeCode?.uppercase().orEmpty()
    PHOTO_BY_TYPE[code]?.let { return it }
    return when (category) {
        2 -> R.drawable.photo_single_piston   // light, under 15,500 lb
        3 -> R.drawable.photo_bizjet          // small, 15,500-75,000 lb
        else -> R.drawable.photo_generic      // large, heavy, military, unknown
    }
}

@Composable
internal fun AircraftPhoto(typeCode: String?, category: Int) {
    val res = remember(typeCode, category) { aircraftPhotoRes(typeCode, category) }
    Box(
        Modifier
            .size(width = 78.dp, height = 58.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(SignBg)
            .border(1.dp, Line, RoundedCornerShape(7.dp))
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = "Aircraft photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
