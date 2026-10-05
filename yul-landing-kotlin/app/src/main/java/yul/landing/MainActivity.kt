@file:Suppress("SpellCheckingInspection")

package yul.landing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val Asphalt = Color(0xFF1D242C)
private val Panel = Color(0xFF262F38)
internal val Line = Color(0xFF3A4551)
private val PaintColor = Color(0xFFF3F4EF)
private val Dim = Color(0xFF97A3AE)
private val Taxi = Color(0xFFF2B705)
private val Alert = Color(0xFFEE6A5C)
internal val SignBg = Color(0xFF0D1013)
private val SignFg = Color(0xFFFFC933)

data class Runway(val name: String, val id: String, val pair: String, val heading: Double)
private val RUNWAYS = listOf(
    Runway("06 (06L/06R)", "06", "06L / 06R", 43.0),
    Runway("24 (24L/24R)", "24", "24L / 24R", 223.0)
)
private const val AIRPORT_LAT = 45.4706
private const val AIRPORT_LON = -73.7408
// Runway surface height (24R is about 102 ft). The 118 ft airport elevation is the highest
// point of the field, which made planes near touchdown read slightly below zero.
private const val AIRPORT_ELEV_M = 31.0
private const val MAX_AGL_M = 1600.0   // ~5,250 ft: covers level intercepts out to the edge of the search area
private const val HDG_TOL = 15.0
private const val CL_TOL = 25.0
// Lane geometry from YUL threshold coordinates, measured in the 06 frame
// (positive = southeast / right of the 06 direction). The midline between the two
// parallel runways sits ~0.15 km southeast of the airport reference point, and each
// runway centerline is ~0.82 km either side of that midline.
private const val MIDLINE_BIAS_KM = 0.15
private const val LANE_HALF_SPACING_KM = 0.82
// A plane only gets L/R once it is within this distance of a runway centerline
// (i.e. established on final, not still being vectored).
private const val LANE_TOL_KM = 0.4
// Only call L/R once the plane is this close and this low; farther out it may
// still be being vectored onto final, so we show just the runway number.
private const val LANE_MAX_KM = 10.0
// OpenSky aircraft categories counted as airplanes:
// 2 = light, 3 = small, 4 = large, 5 = high-vortex large, 6 = heavy, 7 = high performance.
// (8 = rotorcraft, 9 = glider, 12 = ultralight, 14 = UAV are left out.)
private val AIRPLANE_CATEGORIES = setOf(2, 3, 4, 5, 6, 7)
// An aircraft is kept only if it is positively identified as an airplane: its ICAO type is on
// AIRPLANE_TYPES below, or its transponder reports an airplane category. One that is
// identified as something else (helicopter, glider...) is dropped. For aircraft with no
// information at all (lookup failed AND no category): true = keep, false = drop.
private const val INCLUDE_UNIDENTIFIED = true
// Airplane type designators: every ICAO Doc 8643 type with class L (landplane), from the
// Mictronics / tar1090-db type table. Helicopters (H), gyrocopters (G), tiltrotors (T),
// seaplanes (S) and amphibians (A) are not on it.
private val AIRPLANE_TYPES: Set<String> = """
A1 A10 A122 A124 A140 A148 A158 A16 A178 A19 A19N A20 A20N A21 A210 A211 A21N A22 A223 A225 A23 A27 A270
A29 A3 A306 A30B A31 A310 A318 A319 A320 A321 A33 A332 A333 A337 A338 A339 A342 A343 A345 A346 A35 A359
A35K A37 A388 A3ST A4 A400 A411 A50 A500 A504 A6 A660 A700 A743 A748 A890 A9 A900 A910 AA1 AA37 AA5 AAT3
AAT4 AB11 AB15 AB18 AB95 AC11 AC4 AC50 AC52 AC56 AC5A AC68 AC6L AC72 AC80 AC90 AC95 ACAM ACAR ACED ACPL
ACR2 ACRD ACRO ACSR AD20 ADVE AE45 AEA1 AERK AEST AFOX AG02 AG04 AG10 AG14 AI10 AIGT AIRD AIRL AJ27 AJET
AK1 AKNC AKRO ALBU ALC1 ALGR ALIG ALIZ ALPI ALSL ALTO AM3 AMX AN12 AN2 AN22 AN24 AN26 AN28 AN3 AN30 AN32
AN38 AN70 AN72 AN8 ANDR ANGL ANKA ANSN AP20 AP22 AP26 AP28 AP32 AP36 APM2 APM3 APM4 APUP AR11 AR15 AR50
AR5T AR65 AR6T AR79 ARES ARKS ARON ARV1 ARVA ARWF AS02 AS14 AS16 AS2T AS80 ASDR ASO4 ASO5 ASOX ASTO ASTR
AT1 AT2P AT3 AT3P AT3T AT43 AT44 AT45 AT46 AT5P AT5T AT6T AT72 AT73 AT75 AT76 AT8T ATAC ATG1 ATIS ATL
ATLA ATP ATTL AU11 AUJ2 AUJ4 AURA AUS3 AUS4 AUS5 AUS6 AUS7 AUS9 AV68 AVID AVIN AVK4 AXE B1 B14A B14B
B14C B17 B18T B190 B2 B209 B21 B23 B23E B24 B25 B26 B26M B29 B350 B360 B36T B37M B38M B39M B3XM B461
B462 B463 B52 B58T B60 B60T B701 B703 B712 B720 B721 B722 B732 B733 B734 B735 B736 B737 B738 B739 B741
B742 B743 B744 B748 B74R B74S B752 B753 B762 B763 B764 B772 B773 B778 B779 B77L B77W B788 B789 B78X BA11
BAR6 BARC BASS BBAT BBIR BCA3 BCAT BCMP BCS1 BCS3 BD10 BD12 BD17 BD4 BD5 BD5J BD5T BDOG BE10 BE17 BE18
BE19 BE20 BE22 BE23 BE24 BE30 BE32 BE33 BE35 BE36 BE40 BE4W BE50 BE55 BE56 BE58 BE60 BE65 BE70 BE76 BE77
BE80 BE88 BE95 BE99 BE9L BE9T BEA5 BEAR BELF BETA BEVR BF19 BFIT BILO BIPL BIRD BISC BKUT BL11 BL17 BL19
BL8 BLBU BLCF BLEN BLKS BLSA BM6 BMAN BN2P BN2T BO40 BOBC BOLT BOOM BPAT BPOD BPUM BR14 BR23 BR60 BR61
BR8 BRAV BREZ BROU BS60 BSTR BSWN BT36 BT7 BTB2 BTB3 BTUB BU20 BU31 BU33 BU81 BUC BULT BUSH BW60 BW6T
BX2 C02T C04T C06T C07T C08T C1 C101 C10T C119 C120 C123 C125 C130 C135 C140 C141 C14T C15 C150 C152
C160 C162 C17 C170 C172 C175 C177 C180 C182 C185 C188 C190 C195 C2 C205 C206 C207 C208 C210 C212 C21T
C22J C240 C25A C25B C25C C25M C270 C27J C295 C303 C306 C309 C30J C310 C311 C320 C335 C336 C337 C340 C365
C402 C404 C408 C411 C414 C42 C421 C425 C441 C46 C500 C501 C510 C525 C526 C550 C551 C55B C560 C56X C5M
C650 C680 C68A C700 C72R C750 C77R C82 C82R C82S C82T C919 C97 CA12 CA19 CA1P CA1T CA25 CA3 CA4 CA41 CA6
CA61 CA65 CA7P CA7T CA8 CA9 CABI CABN CAD2 CAD4 CAJ CAML CAMP CAN4 CAPL CAR CARV CASS CAT1 CAT2 CAW CB1
CC11 CC19 CDC6 CDW1 CE43 CEGL CEL5 CELR CENT CG3 CH1 CH10 CH15 CH18 CH20 CH25 CH2T CH3 CH30 CH40 CH50
CH60 CH62 CH64 CH65 CH70 CH75 CH7A CH7B CH80 CHAN CHCS CHGO CHIC CHIN CHIP CHP2 CHR1 CHR4 CICA CJ1 CJ6
CKUO CL30 CL35 CL41 CL4G CL60 CL8 CLA CLB1 CLBR CLDS CMA3 CMAS CN12 CN35 CNBR CNDR CNGP CNUK CO50 COAR
COBR COL3 COL4 COLT CONI CORO CORR CORS CORV COUG COUR COY2 COZJ COZY CP10 CP13 CP20 CP21 CP22 CP23 CP30
CP32 CP60 CP65 CP75 CP80 CP90 CPNA CPUP CR10 CRA1 CRAC CRBN CRER CRES CRJ1 CRJ2 CRJ7 CRJ9 CRJX CRUZ CT4
CTAH CUB2 CUCA CUDA CULP CULV CULX CVLP CVLT CX5 CYCL CYGT D1 D11 D139 D140 D150 D18 D201 D21 D228 D24
D25 D250 D253 D28D D28T D31 D328 D39 D4 D5 D5TU D6 D6CR D7 D8 DA2 DA36 DA40 DA42 DA5 DA50 DA62 DAHU DAKH
DAL1 DAL4 DAL5 DART DC10 DC2 DC3 DC3S DC3T DC4 DC6 DC7 DC85 DC86 DC87 DC91 DC92 DC93 DC94 DC95 DDUC DEFI
DELF DF10 DF1M DF2 DF3 DFL6 DFLY DG15 DH2T DH3T DH4T DH60 DH80 DH82 DH83 DH84 DH85 DH87 DH88 DH89 DH8A
DH8B DH8C DH8D DH90 DH94 DHA3 DHC1 DHC2 DHC3 DHC4 DHC5 DHC6 DHC7 DIES DIJ3 DIMO DINO DJET DO27 DO28 DOCX
DON DOVE DR1 DR10 DR22 DR30 DR40 DRIF DRTG DSA1 DSK DSLK DT45 DTA1 DTA2 DUB2 DUCE DUR5 DV1 DV2 DV20 DW1
DWD2 E110 E120 E121 E135 E145 E170 E190 E195 E2 E200 E230 E275 E290 E295 E2CB E300 E314 E350 E35L E390
E3CF E3TF E400 E45X E500 E50P E530 E545 E550 E55P E6 E737 E75L E75S E767 E7BH EA40 EA50 EAEA EAGL EAGX
EBOY EC6 ECHO EDGE EDGT EF2 EFAN EFOX EFUS EGRT EL20 ELF ELIT ELPS ELSP ELST ELTR EM10 EM11 EP9 EPER
EPIC EPX1 ERAC ERCO ES13 ESCA ESCP ESQL ETAR EUFI EUPA EURT EV55 EV97 EVAN EVIC EVOP EVOT EVSS EX5T EXNG
EXPR EZFL EZFT EZHV EZIK EZKC F1 F100 F104 F106 F111 F117 F13 F14 F15 F156 F16 F16X F18H F18S F1FV F2
F22 F260 F26T F27 F28 F2TH F30 F35 F3F F4 F402 F406 F41E F421 F5 F50 F5SA F60 F600 F7 F70 F8 F86 F8L
F900 F9F FA01 FA02 FA03 FA04 FA10 FA11 FA20 FA24 FA50 FA62 FA6X FA7X FA8X FAET FALC FALM FANL FANT FB1A
FB1B FB5 FBA2 FBIR FC1 FD2E FDCT FDF2 FDMC FE51 FEST FFLY FG01 FGT FIBO FIKD FIKE FINC FJ10 FJR3 FK12
FK14 FK9 FL3 FL53 FL54 FL55 FLAM FLCO FLE2 FLE7 FLIZ FLSH FLSS FM25 FMGO FNKB FOOF FORT FOUG FOX FOXT
FRBD FREE FRNT FRON FS51 FT30 FU24 FURY FUSI FW19 FW21 FW44 FW90 FX1 G1 G109 G115 G120 G12T G140 G150
G159 G15T G160 G164 G180 G200 G202 G222 G250 G280 G2GL G2T1 G3 G300 G46 G4SG G59 G64T G70 G800 G850 G91
G96 G97 GA10 GA20 GA3C GA4C GA5C GA6C GA7 GA7C GA8 GA8C GABR GALX GANT GAUN GAVI GB1 GBSP GC1 GEMI GENI
GEPE GF20 GFLY GJ11 GL5T GL7T GLAD GLAS GLEX GLF2 GLF3 GLF4 GLF5 GLF6 GLID GLIM GLSP GLST GLTU GM01 GM17
GMGC GNAT GOLF GOTR GP1 GP4 GPRO GR51 GRAF GRFN GRIF GRIZ GSIS GSPN GUEP GURI GX GY10 GY20 GY30 GY80
H111 H202 H204 H207 H25A H25B H25C H40 HA31 HA4T HAHU HANS HAR HAWK HB21 HB23 HB3 HCAT HD34 HDJT HEAD
HERN HF20 HI27 HIGH HIND HL2 HLC4 HLD4 HM38 HN70 HORN HORZ HPZL HR10 HR20 HRM9 HRNT HROC HRON HRYA HT16
HT2 HT32 HT34 HT36 HT40 HU1 HU2 HUML HUMM HUNT HURI HURK HUSK HYPR I103 I112 I114 I115 I11B I153 I15B
I16 I22 I23 I3 I66 I828 IA46 IA50 IA51 IA58 IA63 IFUR IL14 IL18 IL28 IL38 IL62 IL76 IL86 IL96 IMPU INCQ
INEC INEX INTG ION IP06 IP10 IP26 IP6A IPAN IR21 IR22 IR23 IR24 IR25 IR27 IR28 IR31 IR46 IR99 IRBS IS28
ISAT ISPT J1 J10 J177 J2 J20 J3 J300 J328 J4 J400 J40E J5 J600 J8A J8B JAB2 JAB4 JABI JACE JAGR JAJ5
JAJ6 JARO JAST JB15 JC01 JC02 JCOM JCRU JD2 JDOE JFOX JH7 JK05 JL9 JN76 JPM1 JPRO JRC1 JS1 JS20 JS3 JS31
JS32 JS41 JSQA JSX JT2 JU52 JUN1 JUN2 JUNR JUPI K100 K200 K250 K35E K35R K50 K51 K8 K900 KAAN KAFI KAK1
KAK2 KAK3 KAT3 KATB KATR KC2 KE3 KEHA KELA KELD KERO KEST KF21 KFAB KFAS KFIR KIS2 KIS4 KITH KITI KK60
KL07 KL10 KL25 KL35 KLBR KM2 KNTW KOLL KP2 KP5 KR1 KR2 KR21 KR30 KR31 KR34 KRAG KRAH KRIC KSTK KT1 KTOO
KZ2 KZ3 KZ4 KZ7 KZ8 KZLA L10 L101 L11 L11E L12 L13 L13M L13S L14 L15 L159 L18 L181 L188 L200 L29 L29A
L29B L37 L380 L39 L40 L410 L5 L59 L60 L610 L70 L8 L90 LA60 LA6T LACO LAKR LAKX LANC LAR1 LARK LAST LBUG
LCA LCB LCR LEG2 LEGD LEOP LESP LEV2 LEV4 LEVI LGEZ LGND LH10 LIBE LION LJ23 LJ24 LJ25 LJ28 LJ31 LJ35
LJ40 LJ45 LJ55 LJ60 LJ70 LJ75 LJ85 LM5 LM5X LM7 LMC1 LMK1 LN27 LNC2 LNC4 LNCE LNP4 LNT4 LOCA LOVE LP1
LS2 LSTR LTNG LUL5 LUL6 LUL7 LUL8 LV51 LW20 LW40 LWIN LX32 LX34 LXR LYSA M10 M101 M106 M108 M10R M110
M15 M17 M18 M18T M1SC M2 M200 M203 M20P M20T M21 M212 M22 M24 M26 M28 M2HK M308 M326 M339 M345 M346 M360
M36J M4 M404 M5 M55 M6 M600 M7 M700 M7T M8 M9 MA1 MA5 MA60 MA6H MAGC MAGI MAGN MAJR MAKO MAMB MAME MAVR
MB70 MB80 MC01 MC10 MC23 MC45 MC90 MCOU MCOY MCR1 MCR4 MCRR MCUL MD11 MD3 MD3R MD81 MD82 MD83 MD87 MD88
MD90 ME08 ME09 ME62 MEAD MEL2 MERK MESS METR MEXP MF10 MF17 MG15 MG17 MG19 MG21 MG23 MG25 MG29 MG31 MG44
MGAT MGIC MGNM MGUL MH02 MH46 MIDR MIMP MIMU MIR2 MIRA MITE MJ10 MJ12 MJ1H MJ2 MJ3 MJ4 MJ5 MJ51 MJ53
MJ55 MJ7 MJ77 MJ8 MJ80 MJ9 MJ90 MLER MMAC MMUT MNEX MOCU MOGO MOL1 MON2 MONA MONI MOR2 MOSP MOSQ MOTO
MP02 MP20 MR25 MR35 MR3T MRAI MRAM MRF1 MRJ7 MRJ9 MRTN MS1 MS18 MS23 MS25 MS30 MS31 MS73 MS76 MSAI MSQ2
MT2 MU2 MU23 MU30 MUS2 MVN1 MVRK MX10 MX1T MX2 MX58 MX65 MX80 MXS MY12 MY13 MYA4 MYS4 N110 N120 N219
N250 N260 N262 N3 N320 N340 N3N N5 N5A N5B NAL2 NARN NAVI NC85 ND1T NDAC NDAT NDIC NFEX NG4 NG5 NG5E
NHCO NI28 NIBB NIPR NM5 NMCU NNJA NOMA NORA NORS NPOR NST6 NSTR NT10 NXT NXTE O1 O3 OCNR OM1 OMAG OMGA
OMLA ONE ONEX OPCA OSCR OUDE OVOD OZZI P06T P1 P100 P130 P148 P149 P180 P18T P19 P1HH P2 P208 P210 P212
P220 P230 P250 P25B P27 P270 P28A P28B P28R P28S P28T P28U P3 P32R P32T P337 P38 P39 P40 P46T P47 P4Y
P50 P51 P57 P60 P61 P63 P66P P66T P68 P68T P70 P750 P8 P80 P82 PA11 PA12 PA14 PA15 PA16 PA17 PA18 PA20
PA22 PA23 PA24 PA25 PA27 PA30 PA31 PA32 PA34 PA36 PA38 PA44 PA46 PA47 PACE PAGO PANT PAR1 PAR4 PARL PAT2
PAT4 PAUL PAY1 PAY2 PAY3 PAY4 PC12 PC21 PC24 PC6P PC6T PC7 PC9 PDIG PECR PEGA PEGZ PELI PEMB PETL PGEE
PGK1 PHNX PIAE PIAT PICO PILL PINO PIPA PISI PIT4 PIVE PIVI PK11 PK15 PK18 PK19 PK21 PK23 PK25 PKAN PL1
PL12 PL2 PL4 PL9 PLUS PNR2 PNR3 PNR4 PNTH PO2 PO60 POLI PP2 PP3 PPRO PRBP PRBR PRCE PREN PRET PREX PRIM
PRM1 PROC PROT PROW PRPR PRTS PRXT PSTM PT21 PT22 PT70 PT80 PTMS PTRL PTS1 PTS2 PTSS PUL6 PULR PULS PUP
PURS PUSH PW4 PZ01 PZ02 PZ04 PZ05 PZ06 PZ12 PZ26 PZ3T PZ4M PZ6T Q01 Q1 Q25 Q28 Q4 Q5 Q58 Q9 QAIL QALT
QEST QIC2 QR01 QUAS QUIC R100 R109 R11 R12 R135 R185 R200 R300 R721 R722 R90F R90R R90T RA14 RA17 RAID
RAIL RALL RANG RARO RAV3 RAV5 RAZM RBEL RC70 RCAL RD03 RD20 RDH2 RELI RENE RF10 RF3 RF4 RF47 RF5 RF6 RF9
RFAL RGNT RISN RJ03 RJ1H RJ70 RJ85 RK5 RLU1 RNGR ROAR RODS ROND ROSE RS12 RS18 RS20 RS21 RTA4 RUBI RV10
RV12 RV14 RV15 RV3 RV4 RV4T RV6 RV7 RV8 RV9 RVST RW19 RW20 RW22 RW26 RW3 RYSA RYST S05F S05R S1 S10 S108
S11 S122 S15S S15U S160 S200 S208 S21 S211 S223 S22T S2P S2T S3 S355 S37 S4 S401 S45 S450 S51D S522 S6
S601 S716 S900 SA02 SA03 SA04 SA05 SA10 SA11 SA2 SA3 SA30 SA37 SA38 SA50 SA6 SA6E SA7 SA70 SA75 SA8T
SAB2 SABA SABW SACE SACR SAFF SAH1 SAKO SALB SAM SAPH SASH SASP SASY SATA SAVA SAVG SB05 SB20 SB29 SB32
SB35 SB37 SB39 SB7 SB91 SBD SBLS SBM3 SBOY SBR1 SBR2 SC01 SC7 SCAM SCEP SCOM SCRO SCTR SCUB SCW1 SD2
SD26 SD4 SDUS SE1 SE5A SE5R SERA SF2 SF23 SF24 SF25 SF28 SF31 SF32 SF34 SF35 SF36 SF50 SG37 SG70 SG92
SGCD SGEF SGRA SGUP SH33 SH36 SHAC SHAW SHEK SHER SHOE SHOP SHOR SHRA SHRK SHRT SIDE SIGM SILH SIR2 SIRA
SJ30 SJET SK10 SK70 SKAR SKRA SKYC SKYO SKYR SL1 SL39 SL90 SLCH SLG2 SLG4 SLH4 SLK3 SLK5 SM01 SM19 SM20
SM60 SM92 SMB2 SNAP SNGY SNOS SNS2 SNS7 SNS9 SOK2 SOKL SOL1 SOL2 SONX SORA SP20 SP33 SP55 SP6E SP7 SP91
SP95 SPA2 SPAR SPC2 SPDR SPEL SPIR SPIT SPKR SPOR SPR2 SPRT SPST SPUP SQ2T SQES SR01 SR20 SR22 SRAC SRAI
SRAS SS2 SS2P SS2T SSAB SSTL ST1 ST10 ST3 ST30 ST4 ST50 ST6 ST60 ST75 ST87 STAL STAR STAT STCH STFF STG2
STIL STLN STOR STRA STRE STRI STRK STRM STST SU17 SU24 SU25 SU26 SU27 SU29 SU31 SU38 SU57 SU7 SU80 SU95
SUBA SUNB SUNV SURU SUSO SV4 SVNH SW18 SW2 SW3 SW4 SWAK SWAT SWFT SWIF SWIN SWOR SX30 SYMP SYNC SZ45
SZ9M T1 T10 T101 T134 T154 T160 T18 T19 T2 T204 T206 T210 T211 T22M T250 T28 T30 T33 T334 T34P T34T T35
T37 T38 T4 T40 T411 T415 T419 T5 T50 T51 T5YY T6 T7 TA15 TA20 TAA1 TAGO TAIL TAMP TARO TARR TAYA TAYB
TAYD TB05 TB20 TB21 TB30 TB31 TBM TBM7 TBM8 TBM9 TBR3 TC2 TCAT TCOU TD1 TD2 TD3 TERM TERR TEX2 TEXA TF19
TF21 TF22 TFOC TFUN TGRS TIAD TIJU TIPB TJET TL20 TL30 TLEG TM5 TMOT TMUS TNAV TNDR TOBA TOOT TOR TOUR
TOXO TP40 TPIL TPIN TR20 TR26 TR55 TRAL TRAP TRBA TRDO TRF1 TRIM TRIS TRMA TRWN TS11 TS1J TS8 TSPT TSTN
TT62 TTRS TTWO TU16 TU22 TU95 TUCA TUCR TUCT TUL3 TUTR TVL4 TVLB TWEN TWIR TWSP TWST TZRV U15 U2 U21 U22
UBAT UF10 UF13 UL10 UL20 UL2F UL39 UL45 ULAC ULPA ULTR UNIV URRA UT60 UT65 UT66 UT75 UU12 V1 V10 V221
V252 V322 V351 V452 V8SP VALI VAMP VANT VAUT VELO VELT VEZE VF2 VF35 VF60 VG3T VGUL VIMA VIMY VIPJ VIPR
VISI VIX VIXN VK3P VK3T VL3 VL3T VLOT VLTT VM1 VMT VNOM VNTR VO10 VOL2 VP2 VR20 VR7 VSON VTOR VTRA VTUR
VUT1 VVIG VW10 VWIT W11 W135 W201 W5BC W62T WA40 WA41 WA42 WA50 WA80 WAC9 WACA WACC WACD WACE WACF WACG
WACM WACN WACO WACT WAIX WB57 WBOO WCAT WDEX WF4U WFOC WFUR WH1 WH4 WHAT WHIL WHIS WHIT WHK2 WHKN WICH
WILT WIND WINE WIRR WISP WLBY WM2 WOPU WP40 WP47 WS22 WSP WT10 WT9 WW1 WW23 WW24 WX35 WZER X29 X32 X4
X47B X55 X59 XA41 XA42 XA85 XAIR XB1 XL2 XNOS Y11 Y112 Y12 Y12F Y130 Y141 Y18T Y20 YA1 YAK3 YAK9 YALE
YARR YAST YC12 YK11 YK12 YK18 YK28 YK30 YK38 YK40 YK42 YK50 YK52 YK53 YK54 YK55 YK58 YL15 YS11 YUKN YUNO
YURO Z22 Z26 Z37P Z37T Z42 Z43 Z50 ZEP2 ZEPH ZERO ZIA ZIU ZULU
""".trim().split(Regex("\\s+")).toSet() + setOf("E175", "DA20", "M20", "Z37")
// Fixed search area. Planes are usually level around 3,000-5,000 ft when they join the
// approach 20-25 km out, so this reaches the start of final.
private const val SEARCH_RADIUS_KM = 25
private const val LANE_MAX_AGL_M = 700.0
// --- GLIDESLOPE (optional: delete these, the filter in arrivals() and ApproachProfile to remove) ---
private const val GS_M_PER_KM = 52.4          // 3 degree slope: tan(3 deg) * 1000 m
private const val GS_FT_PER_KM = 172.0        // same slope in feet per km
private const val GS_UPPER_FACTOR = 2.0       // above 2x the slope height = not on final
private const val GS_LOWER_FACTOR = 0.5       // below 0.5x = outside the "normal" band (dimmed in the chart)
private const val GS_FLOOR_M = 100.0          // slack so planes near the runway are never dropped

data class Aircraft(
    val callsign: String, val runway: Runway, val heading: Int,
    val altFt: Int, val distKm: Double, val speedKt: Int?,
    val side: String?, // "L" or "R"; null while too far out / too high to tell
    val category: Int, // OpenSky aircraft category; 0 or 1 = not reported
    val icao24: String = "",
    val typeCode: String? = null, // ICAO type designator from adsbdb, e.g. "B738"
    val maker: String? = null     // manufacturer from adsbdb, e.g. "Boeing"
)
private data class TypeInfo(val code: String, val maker: String?)
data class Metar(val dir: Int?, val speed: Int?, val raw: String, val obsMs: Long?)
data class ForecastRow(
    val date: String, val clock: String, val isNow: Boolean, val code: Int,
    val isDay: Int, val temp: Double?, val dir: Double?, val speed: Double?,
    val gust: Double?, val pop: Int?, val visKm: Double?, val fav: Runway?
)

data class UiState(
    val lang: String = "en",
    val arrivals: List<Aircraft>? = null,
    val trafficError: String? = null,
    val updated: Long? = null,
    val metar: Metar? = null,
    val metarLoaded: Boolean = false,
    val metarError: Boolean = false,
    val forecast: List<ForecastRow>? = null,
    val forecastError: Boolean = false,
    val forecastUpdated: Long? = null,
    val intervalSec: Int = 30,
    val autoRefresh: Boolean = false,
    val busy: Boolean = false,
    val nextAt: Long = System.currentTimeMillis(),
    val status: String = ""
)

private class YulRepository {
    private fun get(url: String, connectMs: Int = 15_000, readMs: Int = 20_000): String = HttpURLConnection::class.java.let {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.requestMethod = "GET"
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            val code = c.responseCode
            if (code == 429) throw ApiException("RATE")
            if (code !in 200..299) throw ApiException("HTTP:$code")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    suspend fun arrivals(radiusKm: Int): List<Aircraft> = withContext(Dispatchers.IO) {
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * cos(Math.toRadians(AIRPORT_LAT)))
        val url = "https://opensky-network.org/api/states/all?" +
                "lamin=${"%.4f".format(Locale.US, AIRPORT_LAT - dLat)}&" +
                "lamax=${"%.4f".format(Locale.US, AIRPORT_LAT + dLat)}&" +
                "lomin=${"%.4f".format(Locale.US, AIRPORT_LON - dLon)}&" +
                "lomax=${"%.4f".format(Locale.US, AIRPORT_LON + dLon)}&" +
                "extended=1"   // adds the aircraft category (index 17) to each state
        val root = JSONObject(get(url))
        val states = root.optJSONArray("states") ?: JSONArray()
        val out = mutableListOf<Aircraft>()
        for (i in 0 until states.length()) {
            val s = states.optJSONArray(i) ?: continue
            val lon = s.optDoubleOrNull(5) ?: continue
            val lat = s.optDoubleOrNull(6) ?: continue
            val track = s.optDoubleOrNull(10) ?: continue
            if (s.optBoolean(8, false)) continue
            // Airplanes only. If the response has no category field at all, don't filter,
            // so a wrong field position can never hide every aircraft.
            val hasCategory = s.length() > 17
            val category = if (hasCategory && !s.isNull(17)) s.optInt(17) else 0
            if (hasCategory && category > 1 && category !in AIRPLANE_CATEGORIES) continue   // transponder says not an airplane
            val alt = s.optDoubleOrNull(13) ?: s.optDoubleOrNull(7) ?: continue
            val agl = alt - AIRPORT_ELEV_M
            if (agl > MAX_AGL_M) continue
            val vr = s.optDoubleOrNull(11)
            // Skip climbing traffic only. Planes are often level while capturing the
            // glideslope 15-25 km out, so level flight must still count as on final.
            if (vr != null && vr > 1.0) continue
            val dist = distanceKm(lat, lon)
            if (dist > radiusKm) continue
            // GLIDESLOPE CHECK (optional): skip planes far above a 3 degree path to the runway
            if (agl > GS_UPPER_FACTOR * GS_M_PER_KM * dist + GS_FLOOR_M) continue
            val rwy = closestRunway(track)
            if (angleDiff(track, rwy.heading) > HDG_TOL) continue
            val brg = bearingDeg(lat, lon)
            if (angleDiff(brg, (rwy.heading + 180) % 360) > CL_TOL) continue
            val call = s.optString(1).trim().ifEmpty { s.optString(0, "?") }
            // Cross-track from the midline, positive = right of the landing direction.
            // The midline offset is defined in the 06 frame, so it flips sign for 24.
            val xt = crossTrackKm(lat, lon, rwy.heading) - (if (rwy.id == "06") MIDLINE_BIAS_KM else -MIDLINE_BIAS_KM)
            val established = abs(abs(xt) - LANE_HALF_SPACING_KM) <= LANE_TOL_KM
            val side = if (dist > LANE_MAX_KM || agl > LANE_MAX_AGL_M || !established) null
            else if (xt >= 0) "R" else "L"
            out += Aircraft(call, rwy, track.roundToInt(), (agl * 3.28084).roundToInt().coerceAtLeast(0), dist,
                s.optDoubleOrNull(9)?.let { (it * 1.94384).roundToInt() }, side, category, s.optString(0))
        }
        out.sortedBy { it.distKm }.map { a ->
            val info = lookupType(a.icao24)
            if (info == null) a else a.copy(typeCode = info.code, maker = info.maker)
        }.filter { isPlane(it) }
    }

    // Include-list decision. Positive identification as a plane wins; a known type that is not
    // on the list (or a non-plane category) is dropped; no information at all follows the flag.
    private fun isPlane(a: Aircraft): Boolean {
        val byType = a.typeCode?.let { it in AIRPLANE_TYPES }   // null = type not known
        return when {
            byType == true -> true
            a.category in AIRPLANE_CATEGORIES -> true
            byType == false -> false
            else -> INCLUDE_UNIDENTIFIED
        }
    }

    // Aircraft type from the free adsbdb.com database, cached per aircraft (types don't change).
    // Misses and errors are remembered for 10 minutes so a failing lookup can't slow every refresh.
    private val typeCache = HashMap<String, Pair<TypeInfo?, Long>>()
    private fun lookupType(icao24: String): TypeInfo? {
        if (icao24.isBlank()) return null
        val now = System.currentTimeMillis()
        synchronized(typeCache) {
            typeCache[icao24]?.let { (info, expiry) -> if (now < expiry) return info }
        }
        val info = runCatching {
            JSONObject(get("https://api.adsbdb.com/v0/aircraft/$icao24", 4_000, 4_000))
                .optJSONObject("response")?.optJSONObject("aircraft")?.let { o ->
                    val code = o.optString("icao_type").trim()
                    if (code.isEmpty()) null else TypeInfo(code, o.optString("manufacturer").trim().ifEmpty { null })
                }
        }.getOrNull()
        synchronized(typeCache) { typeCache[icao24] = info to (now + if (info != null) 24 * 3_600_000L else 600_000L) }
        return info
    }

    suspend fun metar(): Metar? = withContext(Dispatchers.IO) {
        val a = JSONArray(get("https://aviationweather.gov/api/data/metar?ids=CYUL&format=json"))
        if (a.length() == 0) return@withContext null
        val m = a.getJSONObject(0)
        Metar(m.optIntOrNull("wdir"), m.optIntOrNull("wspd"), m.optString("rawOb"),
            if (m.has("obsTime")) m.optLong("obsTime") * 1000 else null)
    }

    suspend fun forecast(): List<ForecastRow> = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$AIRPORT_LAT&longitude=$AIRPORT_LON" +
                "&hourly=is_day,temperature_2m,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility" +
                "&wind_speed_unit=kn&timezone=America%2FToronto&forecast_days=2"
        val root = JSONObject(get(url))
        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val offset = root.optInt("utc_offset_seconds", -14400)
        val nowShifted = System.currentTimeMillis() + offset * 1000L
        var start = 0
        for (i in 0 until times.length()) {
            val ts = times.getString(i)
            if (LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000 > nowShifted) { start = i; break }
        }
        val rows = mutableListOf<ForecastRow>()
        for (i in start until min(start + 12, times.length())) {
            val ts = times.getString(i)
            val dir = h.optJSONArray("wind_direction_10m")?.optDoubleOrNull(i)
            val spd = h.optJSONArray("wind_speed_10m")?.optDoubleOrNull(i)
            rows += ForecastRow(ts, ts.substring(11, 16), i == start,
                h.getJSONArray("weather_code").optInt(i),
                h.optJSONArray("is_day")?.optInt(i, 1) ?: 1,
                h.optJSONArray("temperature_2m")?.optDoubleOrNull(i), dir, spd,
                h.optJSONArray("wind_gusts_10m")?.optDoubleOrNull(i),
                h.optJSONArray("precipitation_probability")?.optIntOrNull(i),
                h.optJSONArray("visibility")?.optDoubleOrNull(i)?.div(1000.0),
                favouredRunway(dir, spd))
        }
        rows
    }
}

private class ApiException(val kind: String) : Exception(kind)
private fun JSONArray.optDoubleOrNull(i: Int): Double? = if (isNull(i)) null else optDouble(i).takeUnless { it.isNaN() }
private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
private fun JSONArray.optIntOrNull(i: Int): Int? = if (isNull(i)) null else optInt(i)
private fun rad(d: Double) = Math.toRadians(d)
private fun angleDiff(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)
private fun distanceKm(lat2: Double, lon2: Double): Double {
    val dLat = rad(lat2 - AIRPORT_LAT); val dLon = rad(lon2 - AIRPORT_LON)
    val a = sin(dLat / 2).pow(2) + cos(rad(AIRPORT_LAT)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
    return 2 * 6371.0 * atan2(sqrt(a), sqrt(1 - a))
}
private fun bearingDeg(lat2: Double, lon2: Double): Double {
    val p1 = rad(AIRPORT_LAT); val p2 = rad(lat2); val dl = rad(lon2 - AIRPORT_LON)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
// Signed distance (km) to the RIGHT of a line through the airport reference point
// pointing along `heading`. Positive = right (R), negative = left (L).
private fun crossTrackKm(lat: Double, lon: Double, heading: Double): Double =
    distanceKm(lat, lon) * sin(rad(bearingDeg(lat, lon) - heading))
private fun closestRunway(track: Double) = RUNWAYS.minBy { angleDiff(track, it.heading) }
private fun favouredRunway(dir: Double?, speed: Double?): Runway? =
    if (dir == null || speed == null || speed < 3) null else RUNWAYS.minBy { angleDiff(dir, it.heading) }

internal class YulViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YulRepository()
    private val prefs = app.getSharedPreferences("yulLanding", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState(
        lang = prefs.getString("lang", null) ?: if (Locale.getDefault().language == "fr") "fr" else "en",
        intervalSec = prefs.getInt("interval", 30),
        autoRefresh = prefs.getBoolean("auto", false)
    ))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var forecastAt = 0L

    init { refresh(); ticker() }
    private fun ticker() = viewModelScope.launch {
        while (isActive) {
            delay(Duration.parse("0.5s"))
            val s = _state.value
            if (s.autoRefresh && !s.busy && System.currentTimeMillis() >= s.nextAt) refresh()
            else if (s.autoRefresh) _state.value = s.copy(status = "next:${max(0, ceil((s.nextAt - System.currentTimeMillis()) / 1000.0).toInt())}")
        }
    }
    fun setLang(v: String) { prefs.edit { putString("lang", v) }; _state.value = _state.value.copy(lang = v) }
    fun setInterval(v: Int) { prefs.edit { putInt("interval", v) }; _state.value = _state.value.copy(intervalSec = v, nextAt = System.currentTimeMillis() + v * 1000L) }
    fun setAuto(v: Boolean) { prefs.edit { putBoolean("auto", v) }; _state.value = _state.value.copy(autoRefresh = v, nextAt = System.currentTimeMillis() + _state.value.intervalSec * 1000L) }

    fun refresh() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, trafficError = null)
            val arrivals = runCatching { repo.arrivals(SEARCH_RADIUS_KM) }
            val metar = runCatching { repo.metar() }
            val needForecast = System.currentTimeMillis() - forecastAt > 15 * 60 * 1000
            val forecast = if (needForecast || _state.value.forecast == null) runCatching { repo.forecast() } else null
            if (forecast != null && forecast.isSuccess) forecastAt = System.currentTimeMillis()
            _state.value = _state.value.copy(
                busy = false,
                arrivals = arrivals.getOrNull() ?: _state.value.arrivals,
                trafficError = arrivals.exceptionOrNull()?.let { errorText(it) },
                updated = if (arrivals.isSuccess) System.currentTimeMillis() else _state.value.updated,
                metar = metar.getOrNull(), metarLoaded = true, metarError = metar.isFailure,
                forecast = forecast?.getOrNull() ?: _state.value.forecast,
                forecastError = forecast?.isFailure ?: _state.value.forecastError,
                forecastUpdated = if (forecast?.isSuccess == true) System.currentTimeMillis() else _state.value.forecastUpdated,
                nextAt = System.currentTimeMillis() + _state.value.intervalSec * 1000L,
                status = ""
            )
        }
    }
    private fun errorText(e: Throwable): String = when ((e as? ApiException)?.kind) {
        "RATE" -> "rate"
        else -> "network"
    }
}

private fun t(lang: String, key: String, vararg p: Pair<String, String>): String {
    val en = mapOf(
        "sub" to "Runway in use, read from live aircraft on final approach", "landing" to "Landing direction",
        "arrivals" to "Aircraft on final", "wind" to "Wind at the airport (CYUL observation)",
        "forecast" to "Airport forecast, next 12 hours", "waiting" to "Waiting for first update", "checking" to "Checking",
        "updated" to "Updated {time} (Montréal time)",
        "unavailable" to "Unavailable", "no_arrivals" to "No arrivals", "in_use" to "Runway in use", "favours" to "Wind favours",
        "lane_pending" to "Lane pending", "undetermined" to "Not determined",
        "cat_light" to "Light", "cat_small" to "Small", "cat_large" to "Large", "cat_large_vortex" to "Large (high wake)", "cat_heavy" to "Heavy",
        "cat_high_perf" to "High performance", "cat_rotor" to "Rotorcraft", "cat_glider" to "Glider", "cat_unknown" to "Category unknown", "profile" to "Approach profile", "lane_known" to "Lane known", "runway_only" to "Runway only",
        "profile_note" to "Distance in km, height in ft (heights exaggerated). Dashed line: 3° glideslope; shaded band: range counted as on final. Filled dot: lane known; ring: runway only.",
        "note_none_wind" to "No aircraft on final right now. The wind favours runway {id}, which would mean landing toward the {dir}.",
        "note_none" to "No aircraft on final right now. This can be a quiet spell or a gap in ADS-B coverage.",
        "note_mixed" to "Mixed runway usage: {list}.",
        "note_switch" to "The wind currently favours runway {id}, so traffic may switch soon.",
        "loading" to "Loading.", "noneDetected" to "No aircraft detected on final approach.",
        "trafficUnavailable" to "Live traffic is unavailable right now.",
        "windUnavailable" to "Wind is unavailable right now.", "noMetar" to "No METAR reported.",
        "wind_fav" to "From {dir}° at {spd} kt, which favours runway {rwy}.",
        "calm" to "Wind is calm or variable, so runway choice is not driven by wind.",
        "obs_at" to "Observed {time} at CYUL. ", "obs_none" to "CYUL. ",
        "fcUnavailable" to "Forecast is unavailable right now.", "fcNone" to "No forecast data.",
        "fc_calm" to "Light winds expected: runway choice may not follow the wind.",
        "fc_switch_now" to "Forecast wind favours runway {a} now and is expected to switch to runway {b} around {time}.",
        "fc_switch_later" to "Forecast wind favours runway {a} once the wind picks up and is expected to switch to runway {b} around {time}.",
        "fc_steady_now" to "Forecast wind favours runway {a} now and through the next {n} hours.",
        "fc_steady_later" to "Forecast wind favours runway {a} once the wind picks up, through the next {n} hours.",
        "fc_source" to "Forecast for the airport itself ({lat}, {lon}), from Open-Meteo. Hours are Montréal time ({tz}). Updated {time}.",
        "now" to "Now", "lightWind" to "Light wind",
        "heading" to "Heading", "alt" to "Alt (ft)", "dist" to "Dist (km)", "speed" to "Speed (kt)", "gusts" to "gusts",
        "refresh" to "Refresh now", "auto" to "Auto-refresh", "radius" to "Search radius", "refreshEvery" to "Refresh every",
        "statusNext" to "Next update in {s} s", "updating" to "Updating…",
        "err_network" to "Could not reach OpenSky. Check your connection; if you have refreshed a lot, the daily limit may be used up.",
        "err_rate" to "OpenSky rate limit reached. Choose a longer refresh interval and try again later.",
        "err_generic" to "The live traffic request failed.",
        "fine" to "Live positions come from the OpenSky Network's free ADS-B feed. Anonymous access allows roughly 400 requests a day, so a longer refresh keeps the page working all day. Parallel runways (06L/06R and 24L/24R) share a heading and are reported together. Updates pause while this tab is hidden. All weather is for the airport itself (CYUL observation and a forecast for its coordinates), never for the visitor's location, and every time shown is Montréal time. The forecast comes from Open-Meteo model data and, while auto-refresh is on, refreshes every 15 minutes. Forecast wind only shows which runway it would favour; the runway actually used is chosen by air traffic control.",
        "dirN" to "North", "dirNE" to "Northeast", "dirE" to "East", "dirSE" to "Southeast", "dirS" to "South", "dirSW" to "Southwest", "dirW" to "West", "dirNW" to "Northwest"
    )
    val fr = mapOf(
        "sub" to "Piste en service, déduite des avions en finale en temps réel", "landing" to "Direction d'atterrissage",
        "arrivals" to "Avions en finale", "wind" to "Vent à l'aéroport (observation CYUL)", "forecast" to "Prévisions à l'aéroport, 12 prochaines heures",
        "waiting" to "En attente de la première mise à jour", "checking" to "Vérification",
        "updated" to "Mis à jour {time} (heure de Montréal)",
        "unavailable" to "Indisponible", "no_arrivals" to "Aucune arrivée", "in_use" to "Piste en service", "favours" to "Vent favorable",
        "lane_pending" to "Côté à confirmer", "undetermined" to "Indéterminée",
        "cat_light" to "Léger", "cat_small" to "Petit", "cat_large" to "Grand", "cat_large_vortex" to "Grand (fort sillage)", "cat_heavy" to "Lourd",
        "cat_high_perf" to "Haute performance", "cat_rotor" to "Giravion", "cat_glider" to "Planeur", "cat_unknown" to "Catégorie inconnue", "profile" to "Profil d'approche", "lane_known" to "Côté connu", "runway_only" to "Piste seulement",
        "profile_note" to "Distance en km, hauteur en ft (hauteurs exagérées). Ligne pointillée : pente de 3°; zone ombrée : plage comptée comme en finale. Point plein : côté connu; anneau : piste seulement.",
        "note_none_wind" to "Aucun avion en finale pour le moment. Le vent favorise la piste {id}, ce qui signifierait un atterrissage vers le {dir}.",
        "note_none" to "Aucun avion en finale pour le moment. Il peut s'agir d'une période calme ou d'une lacune de couverture ADS-B.",
        "note_mixed" to "Utilisation mixte des pistes : {list}.",
        "note_switch" to "Le vent favorise actuellement la piste {id} : la circulation pourrait bientôt changer de piste.",
        "loading" to "Chargement…", "noneDetected" to "Aucun avion détecté en approche finale.",
        "trafficUnavailable" to "Le trafic en direct est indisponible pour le moment.",
        "windUnavailable" to "Le vent est indisponible pour le moment.", "noMetar" to "Aucun METAR disponible.",
        "wind_fav" to "Vent du {dir}° à {spd} kt, ce qui favorise la piste {rwy}.",
        "calm" to "Vent calme ou variable : le choix de la piste ne dépend pas du vent.",
        "obs_at" to "Observé à {time} à CYUL. ", "obs_none" to "CYUL. ",
        "fcUnavailable" to "Les prévisions sont indisponibles pour le moment.", "fcNone" to "Aucune donnée de prévision.",
        "fc_calm" to "Vents faibles prévus : le choix de la piste pourrait ne pas suivre le vent.",
        "fc_switch_now" to "Le vent prévu favorise la piste {a} en ce moment, avec un changement attendu vers la piste {b} vers {time}.",
        "fc_switch_later" to "Le vent prévu favorisera la piste {a} dès que le vent se lèvera, avec un changement attendu vers la piste {b} vers {time}.",
        "fc_steady_now" to "Le vent prévu favorise la piste {a} en ce moment et pour les {n} prochaines heures.",
        "fc_steady_later" to "Le vent prévu favorisera la piste {a} dès que le vent se lèvera, pour les {n} prochaines heures.",
        "fc_source" to "Prévisions pour l'aéroport lui-même ({lat}, {lon}), d'après Open-Meteo. Les heures sont l'heure de Montréal ({tz}). Mis à jour à {time}.",
        "now" to "Actuel", "lightWind" to "Vent faible",
        "heading" to "Cap", "alt" to "Alt. (ft)", "dist" to "Dist. (km)", "speed" to "Vit. (kt)", "gusts" to "rafales",
        "refresh" to "Actualiser maintenant", "auto" to "Auto-actualisation", "radius" to "Rayon de recherche", "refreshEvery" to "Actualiser toutes les",
        "statusNext" to "Prochaine mise à jour dans {s} s", "updating" to "Mise à jour…",
        "err_network" to "Impossible de joindre OpenSky. Vérifiez votre connexion; si vous avez beaucoup actualisé, la limite quotidienne est peut-être atteinte.",
        "err_rate" to "Limite de requêtes OpenSky atteinte. Choisissez un intervalle d'actualisation plus long et réessayez plus tard.",
        "err_generic" to "La requête de trafic en direct a échoué.",
        "fine" to "Les positions en direct proviennent du flux ADS-B gratuit du réseau OpenSky. L'accès anonyme permet environ 400 requêtes par jour : un intervalle plus long permet à la page de fonctionner toute la journée. Les pistes parallèles (06L/06R et 24L/24R) ont le même cap et sont regroupées. Les mises à jour sont suspendues lorsque cet onglet est masqué. Toute la météo concerne l'aéroport lui-même (observation CYUL et prévisions pour ses coordonnées), jamais l'emplacement du visiteur, et toutes les heures affichées sont l'heure de Montréal. Les prévisions proviennent des données de modèle d'Open-Meteo et, lorsque l'auto-actualisation est activée, sont actualisées toutes les 15 minutes. Le vent prévu indique seulement la piste qu'il favoriserait; la piste réellement utilisée est choisie par le contrôle de la circulation aérienne.",
        "dirN" to "Nord", "dirNE" to "Nord-est", "dirE" to "Est", "dirSE" to "Sud-est", "dirS" to "Sud", "dirSW" to "Sud-ouest", "dirW" to "Ouest", "dirNW" to "Nord-ouest"
    )
    var s = (if (lang == "fr") fr else en)[key] ?: en[key] ?: key
    p.forEach { (k, v) -> s = s.replace("{$k}", v) }
    return s
}
private fun directionWord(lang: String, deg: Double): String {
    val k = ((round(((deg % 360 + 360) % 360) / 45).toInt()) % 8)
    return t(lang, listOf("dirN","dirNE","dirE","dirSE","dirS","dirSW","dirW","dirNW")[k])
}
private fun fmtTime(ms: Long?, seconds: Boolean = false, lang: String = "en"): String {
    if (ms == null) return ""
    val loc = if (lang == "fr") Locale.CANADA_FRENCH else Locale.CANADA
    val f = if (seconds) DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z", loc) else DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z", loc)
    return f.format(Instant.ofEpochMilli(ms).atZone(ZoneId.of("America/Toronto")))
}
private fun pad3(d: Double?) = d?.roundToInt()?.toString()?.padStart(3, '0') ?: "–"
private fun forecastSummary(lang: String, rows: List<ForecastRow>): String {
    val known = rows.filter { it.fav != null }
    if (known.isEmpty()) return t(lang, "fc_calm")
    val first = known.first().fav!!
    val change = known.firstOrNull { it.fav!!.id != first.id }
    val now = rows.firstOrNull()?.fav != null
    return if (change != null) t(lang, if (now) "fc_switch_now" else "fc_switch_later", "a" to first.id, "b" to change.fav!!.id, "time" to change.clock)
    else t(lang, if (now) "fc_steady_now" else "fc_steady_later", "a" to first.id, "n" to rows.size.toString())
}
private fun nf(d: Double, digits: Int, lang: String): String = NumberFormat.getNumberInstance(if (lang == "fr") Locale.CANADA_FRENCH else Locale.CANADA).apply { minimumFractionDigits = digits; maximumFractionDigits = digits }.format(d)
private fun weather(code: Int, day: Boolean, lang: String): Pair<String, String> {
    val isFr = lang == "fr"
    fun l(en: String, fr: String) = if (isFr) fr else en
    return when (code) {
        0 -> (if (day) "☀️" else "🌙") to l("Clear", "Dégagé")
        1 -> "🌤️" to l("Mainly clear", "Généralement dégagé")
        2 -> (if (day) "⛅" else "☁️") to l("Partly cloudy", "Partiellement nuageux")
        3 -> "☁️" to l("Overcast", "Couvert")
        45 -> "🌫️" to l("Fog", "Brouillard")
        48 -> "🌫️" to l("Freezing fog", "Brouillard givrant")
        51 -> (if (day) "🌦️" else "🌧️") to l("Light drizzle", "Bruine légère")
        53 -> (if (day) "🌦️" else "🌧️") to l("Drizzle", "Bruine")
        55 -> "🌧️" to l("Heavy drizzle", "Forte bruine")
        56, 57 -> "🧊" to l("Freezing drizzle", "Bruine verglaçante")
        61 -> (if (day) "🌦️" else "🌧️") to l("Light rain", "Pluie légère")
        63 -> "🌧️" to l("Rain", "Pluie")
        65 -> "🌧️" to l("Heavy rain", "Forte pluie")
        66, 67 -> "🧊" to l("Freezing rain", "Pluie verglaçante")
        71 -> "🌨️" to l("Light snow", "Faible neige")
        73 -> "🌨️" to l("Snow", "Neige")
        75 -> "❄️" to l("Heavy snow", "Forte neige")
        77 -> "🌨️" to l("Snow grains", "Neige en grains")
        80 -> (if (day) "🌦️" else "🌧️") to l("Light showers", "Averses légères")
        81 -> "🌧️" to l("Showers", "Averses")
        82 -> "🌧️" to l("Heavy showers", "Fortes averses")
        85 -> "🌨️" to l("Snow showers", "Averses de neige")
        86 -> "❄️" to l("Heavy snow showers", "Fortes averses de neige")
        95 -> "⛈️" to l("Thunderstorm", "Orage")
        96, 99 -> "⛈️" to l("Thunderstorm, hail", "Orage avec grêle")
        else -> "❔" to "–"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YulApp() }
    }
}

@Composable
private fun YulApp(vm: YulViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme(background = Asphalt, surface = Panel, primary = Taxi, onBackground = PaintColor, onSurface = PaintColor)) {
        Surface(color = Asphalt, modifier = Modifier.fillMaxSize()) {
            YulScreen(s, vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YulScreen(s: UiState, vm: YulViewModel) {
    val lang = s.lang
    val listState = rememberLazyListState()
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        PullToRefreshBox(isRefreshing = s.busy, modifier = Modifier.fillMaxSize(), onRefresh = { vm.refresh() }) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                item {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Montréal-Trudeau (YUL)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(t(lang, "sub"), color = Dim, fontSize = 14.sp)
                        }
                        Row(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Line)) {
                            LanguageButton("EN", lang == "en") { vm.setLang("en") }
                            LanguageButton("FR", lang == "fr") { vm.setLang("fr") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    UpdatedStamp(if (s.updated != null) t(lang, "updated", "time" to fmtTime(s.updated, true, lang)) else t(lang, "waiting"), s.updated != null)
                    Spacer(Modifier.height(14.dp))
                    if (s.trafficError != null) {
                        Notice(t(lang, if (s.trafficError == "rate") "err_rate" else "err_network"))
                        Spacer(Modifier.height(14.dp))
                    }
                    Hero(s, lang)
                }
                item { ApproachProfile(s.arrivals.orEmpty(), lang) }
                item { SectionTitle(t(lang, "arrivals")) }
                if (s.arrivals == null || s.trafficError != null) item { EmptyText(if (s.trafficError != null) t(lang, "trafficUnavailable") else t(lang, "loading")) }
                else if (s.arrivals.isEmpty()) item { EmptyText(t(lang, "noneDetected")) }
                else items(s.arrivals, key = { it.callsign + it.distKm }) { AircraftCard(it, lang) }
                item { SectionTitle(t(lang, "wind")) }
                item { WindCard(s, lang) }
                item { SectionTitle(t(lang, "forecast")) }
                if (s.forecastError) item { EmptyText(t(lang, "fcUnavailable")) }
                else if (s.forecast == null) item { EmptyText(t(lang, "loading")) }
                else {
                    val rows = s.forecast
                    item { Text(forecastSummary(lang, rows)) }
                    var prevFav: String? = null
                    val cards = rows.map { r ->
                        val switched = r.fav != null && prevFav != null && r.fav.id != prevFav
                        if (r.fav != null) prevFav = r.fav.id
                        r to switched
                    }
                    if (wide) cards.chunked(2).forEach { pair ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { (r, sw) -> ForecastCard(r, sw, lang, Modifier.weight(1f)) }
                            }
                        }
                    } else cards.forEach { (r, sw) -> item { ForecastCard(r, sw, lang) } }
                    item {
                        val time = fmtTime(s.forecastUpdated, true, lang)
                        Text(t(lang, "fc_source", "lat" to "45.4706", "lon" to "-73.7408", "tz" to (time.split(" ").lastOrNull() ?: ""), "time" to time), color = Dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                item { Controls(s, vm, lang) }
                item { Text(t(lang, "fine"), color = Dim, fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun UpdatedStamp(text: String, fresh: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Panel).border(1.dp, if (fresh) Taxi else Line, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(9.dp).background(if (fresh) Taxi else Dim, RoundedCornerShape(50)))
        Text(text, color = PaintColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun LanguageButton(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(containerColor = if (selected) SignBg else Color.Transparent, contentColor = if (selected) SignFg else Dim), modifier = Modifier.height(40.dp)) { Text(text, fontWeight = FontWeight.Bold) }
}
@Composable private fun Notice(text: String) { Box(Modifier.fillMaxWidth().background(Panel).border(3.dp, Alert).padding(10.dp)) { Text(text, color = PaintColor) } }
@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
@Composable private fun EmptyText(text: String) { Text(text, color = Dim, modifier = Modifier.padding(vertical = 2.dp)) }

@Composable private fun Hero(s: UiState, lang: String) {
    val fav = s.metar?.let { favouredRunway(it.dir?.toDouble(), it.speed?.toDouble()) }
    val top = s.arrivals?.groupingBy { it.runway.id }?.eachCount()?.maxByOrNull { it.value }?.key?.let { id -> RUNWAYS.firstOrNull { it.id == id } }
    val active = top ?: fav
    val predicted = top == null
    // Lanes in use, e.g. ["24L", "24R"], and the ones belonging to the dominant runway
    // A 3-char entry ("24R") means the lane is known; a 2-char entry ("24") means only the runway is.
    val lanes = s.arrivals.orEmpty().groupBy { it.runway.id }.flatMap { (id, list) ->
        val sides = list.mapNotNull { it.side }.distinct().sorted()
        if (sides.isEmpty()) listOf(id) else sides.map { id + it }
    }
    val topLanes = if (top == null) emptyList() else lanes.filter { it.length == 3 && it.startsWith(top.id) }
    val dir = when {
        s.arrivals == null -> t(lang, if (s.trafficError != null) "unavailable" else "checking")
        s.arrivals.isEmpty() -> t(lang, "no_arrivals")
        else -> directionWord(lang, top!!.heading)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(t(lang, "landing"), color = Dim, fontSize = 14.sp)
        Text(dir, color = if (s.arrivals.isNullOrEmpty()) Dim else PaintColor, fontSize = if (dir.length > 9) 46.sp else 64.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RunwaySign(active, predicted, lang, if (predicted) null else topLanes.joinToString(" / ").ifEmpty { null }, pending = !predicted && topLanes.isEmpty())
            RunwayDiagram(
                active?.id,
                predicted,
                lanes.toSet(),
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
            )
        }
        val note = when {
            s.arrivals == null -> ""
            s.arrivals.isEmpty() -> if (fav != null) t(lang, "note_none_wind", "id" to fav.id, "dir" to directionWord(lang, fav.heading).lowercase()) else t(lang, "note_none")
            else -> {
                val counts = s.arrivals.groupingBy { it.runway.id }.eachCount()
                when {
                    counts.size > 1 -> t(lang, "note_mixed", "list" to counts.entries.joinToString(", ") { e -> RUNWAYS.first { it.id == e.key }.name + " ×" + e.value })
                    fav != null && fav.id != top?.id -> t(lang, "note_switch", "id" to fav.id)
                    else -> ""
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = Dim)
    }
}

@Composable private fun RunwaySign(r: Runway?, predicted: Boolean, lang: String, lanes: String? = null, pending: Boolean = false) {
    Column(Modifier.padding(4.dp).border(3.dp, if (predicted) Line else SignFg, RoundedCornerShape(12.dp)).background(if (predicted) Color.Transparent else SignBg, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t(lang, if (predicted && r != null) "favours" else "in_use"), color = if (predicted) Dim else Color(0xFFE6E1C6), fontSize = 12.sp)
        Text(r?.id ?: "--", color = if (r == null) Dim else if (predicted) PaintColor else SignFg, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
        Text(lanes ?: (if (pending) t(lang, "lane_pending") else r?.pair ?: t(lang, "undetermined")), color = if (predicted) Dim else Color(0xFFE6E1C6), fontWeight = FontWeight.SemiBold, fontSize = if (lanes != null) 22.sp else 16.sp)
    }
}

@Composable private fun RunwayDiagram(activeId: String?, predicted: Boolean, lanes: Set<String>, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "runwayArrows")
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrowDashPhase"
    )

    Canvas(modifier) {
        val designSize = 260f
        val scale = min(size.width, size.height) / designSize
        val c = Offset(130f * scale, 130f * scale)
        val len = 140f * scale
        val w = 14f * scale
        val off = 20f * scale            // lateral distance of each lane from the midline
        val stroke = max(1f, 1.5f * scale)
        val diagramHeading = 43f
        val th = Math.toRadians(diagramHeading.toDouble()).toFloat()

        // Diagram frame: runways point along heading 43 (the 06 direction), drawn
        // "up". Local +x is to the RIGHT of the 06 direction, so the +off lane is
        // 06R (and 24L when landing the other way); the -off lane is 06L / 24R.
        fun toScreen(lx: Float, ly: Float) = Offset(
            c.x + lx * cos(th) - ly * sin(th),
            c.y + lx * sin(th) + ly * cos(th)
        )
        fun name06(o: Float) = if (o > 0) "06R" else "06L"
        fun name24(o: Float) = if (o > 0) "24L" else "24R"

        // The two parallel runways. Only lanes actually in use are highlighted.
        drawContext.canvas.save()
        drawContext.canvas.rotate(diagramHeading, c.x, c.y)
        listOf(-off, off).forEach { o ->
            // Three looks: lane known (solid), runway known but lane pending (soft glow),
            // and wind-only prediction (dashed).
            val known = name06(o) in lanes || name24(o) in lanes
            val pending = !predicted && !known && ("06" in lanes || "24" in lanes)
            val on = if (predicted) activeId != null else known
            drawRoundRect(
                color = if (on) lerp(Panel, Taxi, .30f) else if (pending) lerp(Panel, Taxi, .15f) else Panel,
                topLeft = Offset(c.x - w / 2f + o, c.y - len / 2f),
                size = Size(w, len),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
            )
            drawRoundRect(
                color = if (on) Taxi else if (pending) Taxi.copy(alpha = .4f) else Line,
                topLeft = Offset(c.x - w / 2f + o, c.y - len / 2f),
                size = Size(w, len),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = if (on) 2.5f * scale else stroke,
                    pathEffect = if (predicted && on) {
                        PathEffect.dashPathEffect(floatArrayOf(7f * scale, 4f * scale))
                    } else null
                )
            )
            drawLine(
                PaintColor.copy(alpha = .6f),
                Offset(c.x + o, c.y - len / 2f + 8f * scale),
                Offset(c.x + o, c.y + len / 2f - 8f * scale),
                strokeWidth = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * scale, 5f * scale))
            )
        }
        drawContext.canvas.restore()

        // One label per runway end (06L, 06R, 24L, 24R), each in line with its lane.
        val tagDist = len / 2f + 20f * scale
        listOf(-off, off).forEach { o ->
            val n06 = name06(o)
            val n24 = name24(o)
            val tagLat = o + (if (o > 0) 38f else -38f) * scale   // outside the lane, clear of the arrow
            textLabel(this, n06, toScreen(tagLat, tagDist), n06 in lanes || "06" in lanes || (predicted && activeId == "06"), scale)
            textLabel(this, n24, toScreen(tagLat, -tagDist), n24 in lanes || "24" in lanes || (predicted && activeId == "24"), scale)
        }

        // Approach arrows: one per lane in use, aimed straight down that lane.
        // Predicted (wind only, lane unknown): a single centred arrow, as before.
        val arrows: List<Pair<Double, Float>> = when {
            activeId == null -> emptyList()
            predicted -> listOf(RUNWAYS.first { it.id == activeId }.heading to 0f)   // centred between the lanes
            else -> lanes.mapNotNull { lane ->
                val r = RUNWAYS.firstOrNull { it.id == (if (lane.length == 2) lane else lane.dropLast(1)) } ?: return@mapNotNull null
                // In the arrow's own frame (rotated by the landing heading), +x is
                // always to the right of travel, so R is +off and L is -off. The arrow
                // sits in line with its lane, just before the runway threshold.
                r.heading to (if (lane.length == 2) 0f else if (lane.last() == 'R') off else -off)   // 0 = lane not known yet, centred
            }
        }
        arrows.forEach { (heading, lat) ->
            val ax = c.x + lat
            val tail = c.y + len / 2f + 54f * scale
            val tip = c.y + len / 2f + 6f * scale
            val arrowAlpha = if (predicted) .55f else 1f
            drawContext.canvas.save()
            drawContext.canvas.rotate(heading.toFloat(), c.x, c.y)
            drawLine(
                Taxi.copy(alpha = arrowAlpha),
                Offset(ax, tail),
                Offset(ax, tip + 14f * scale),
                strokeWidth = 4.5f * scale,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(10f * scale, 8f * scale),
                    if (predicted) 0f else dashPhase * scale
                )
            )
            val head = Path().apply {
                moveTo(ax, tip)
                lineTo(ax - 10f * scale, tip + 17f * scale)
                lineTo(ax + 10f * scale, tip + 17f * scale)
                close()
            }
            drawPath(head, Taxi.copy(alpha = arrowAlpha))
            drawContext.canvas.restore()
        }
    }
}

private fun textLabel(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    text: String,
    p: Offset,
    on: Boolean,
    scale: Float
) {
    val background = if (on) SignBg else Color.Transparent
    val border = if (on) SignFg else Line
    val w = 38f * scale
    val h = 24f * scale

    scope.drawRoundRect(
        color = background,
        topLeft = Offset(p.x - w / 2f, p.y - h / 2f),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale),
    )
    scope.drawRoundRect(
        color = border,
        topLeft = Offset(p.x - w / 2f, p.y - h / 2f),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * scale),
    )

    val canvas = scope.drawContext.canvas.nativeCanvas
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = if (on) android.graphics.Color.rgb(255, 201, 51) else android.graphics.Color.rgb(151, 163, 174)
        textSize = 15f * scale
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText(text, p.x, p.y - (paint.ascent() + paint.descent()) / 2f, paint)
}

// GLIDESLOPE chart (optional): side view of the 3 degree path with each aircraft as a dot.
@Composable private fun ApproachProfile(arrivals: List<Aircraft>, lang: String) {
    val lblKnown = t(lang, "lane_known")
    val lblRunway = t(lang, "runway_only")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(t(lang, "profile"))
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val padL = 34.dp.toPx(); val padR = 30.dp.toPx(); val padT = 10.dp.toPx(); val padB = 20.dp.toPx()
            val plotW = size.width - padL - padR
            val plotH = size.height - padT - padB
            val xMax = SEARCH_RADIUS_KM.toFloat()   // chart spans the whole search area
            val yMax = max(5000f, ceil((arrivals.maxOfOrNull { it.altFt } ?: 0).toFloat() / 1000f) * 1000f)
            val slope = GS_FT_PER_KM.toFloat()
            val floorFt = (GS_FLOOR_M * 3.28084).toFloat()
            fun px(km: Float) = padL + (xMax - km.coerceIn(0f, xMax)) / xMax * plotW
            fun py(ft: Float) = padT + plotH - ft.coerceIn(0f, yMax) / yMax * plotH

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10.sp.toPx(); color = Dim.toArgb()
            }
            val nc = drawContext.canvas.nativeCanvas
            fun text(txt: String, x: Float, y: Float, align: android.graphics.Paint.Align, argb: Int = Dim.toArgb()) {
                paint.textAlign = align; paint.color = argb
                nc.drawText(txt, x, y, paint)
            }

            // grid + ground
            (1000 until yMax.toInt() step 1000).map { it.toFloat() }.forEach { v ->
                drawLine(Line.copy(alpha = .5f), Offset(padL, py(v)), Offset(padL + plotW, py(v)), strokeWidth = 1f)
                text(v.roundToInt().toString(), padL - 4.dp.toPx(), py(v) + 3.dp.toPx(), android.graphics.Paint.Align.RIGHT)
            }
            drawLine(Line, Offset(padL, py(0f)), Offset(padL + plotW, py(0f)), strokeWidth = 1.5f)
            var km = 0
            while (km <= xMax) {
                text("$km km", px(km.toFloat()), size.height - 4.dp.toPx(), android.graphics.Paint.Align.CENTER)
                km += 5
            }

            // corridor counted as "on final" (0.5x to 2x the 3 degree height)
            val up = GS_UPPER_FACTOR.toFloat() * slope
            val lo = GS_LOWER_FACTOR.toFloat() * slope
            val xHit = yMax / up
            val band = Path().apply {
                moveTo(px(0f), py(0f))
                if (xHit < xMax) { lineTo(px(xHit), py(yMax)); lineTo(px(xMax), py(yMax)) } else lineTo(px(xMax), py(up * xMax))
                lineTo(px(xMax), py(min(lo * xMax, yMax)))
                close()
            }
            drawPath(band, Taxi.copy(alpha = .10f))

            // the 3 degree glideslope itself
            val xEnd = min(xMax, yMax / slope)
            drawLine(Dim, Offset(px(0f), py(0f)), Offset(px(xEnd), py(slope * xEnd)), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))

            // runway strip at distance 0
            drawLine(PaintColor.copy(alpha = .8f), Offset(px(0f), py(0f)), Offset(size.width - 4.dp.toPx(), py(0f)), strokeWidth = 4.dp.toPx())

            // lane-decision gate
            val gx = px(LANE_MAX_KM.toFloat())
            drawLine(Dim.copy(alpha = .7f), Offset(gx, padT), Offset(gx, py(0f)), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            text(lblKnown, gx + 4.dp.toPx(), padT + 10.dp.toPx(), android.graphics.Paint.Align.LEFT)
            text(lblRunway, gx - 4.dp.toPx(), padT + 10.dp.toPx(), android.graphics.Paint.Align.RIGHT)

            // aircraft, nearest first
            arrivals.forEachIndexed { i, a ->
                val d = a.distKm.toFloat()
                val x = px(d); val y = py(a.altFt.toFloat())
                val inBand = a.altFt >= lo * d - floorFt && a.altFt <= up * d + floorFt
                val col = if (inBand) Taxi else Dim
                if (a.side != null) drawCircle(col, 5.dp.toPx(), Offset(x, y))
                else drawCircle(col, 5.dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                var ly = y - 9.dp.toPx() - (i % 2) * 11.dp.toPx()
                if (ly < padT + 22.dp.toPx()) ly = y + 18.dp.toPx() + (i % 2) * 11.dp.toPx()
                val lx = x.coerceIn(padL + 22.dp.toPx(), size.width - 22.dp.toPx())
                text(a.callsign, lx, ly, android.graphics.Paint.Align.CENTER, PaintColor.toArgb())
            }
        }
        Text(t(lang, "profile_note"), color = Dim, fontSize = 12.sp)
    }
}

private fun categoryLabel(lang: String, c: Int) = t(lang, when (c) {
    2 -> "cat_light"; 3 -> "cat_small"; 4 -> "cat_large"; 5 -> "cat_large_vortex"; 6 -> "cat_heavy"
    7 -> "cat_high_perf"; 8 -> "cat_rotor"; 9 -> "cat_glider"; else -> "cat_unknown"
})

// Aircraft photos are bundled drawables: see AircraftPhotos.kt.

@Composable private fun AircraftCard(a: Aircraft, lang: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AircraftPhoto(a.typeCode, a.category)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.callsign, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val typeLine = listOfNotNull(a.typeCode, a.maker).joinToString(" · ")
                    Text(typeLine.ifEmpty { categoryLabel(lang, a.category) }, color = Dim, fontSize = 12.sp)
                }
                RunwayBadge(a.runway, stacked = true, side = a.side)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(t(lang,"heading"), "${a.heading}°"); Metric(t(lang,"alt"), nf(a.altFt.toDouble(),0,lang)); Metric(t(lang,"dist"), nf(a.distKm,1,lang)); Metric(t(lang,"speed"), a.speedKt?.toString() ?: "–")
            }
        }
    }
}
@Composable private fun RunwayBadge(r: Runway, highlight: Boolean = false, stacked: Boolean = false, side: String? = null) {
    val content: @Composable () -> Unit = {
        Box(Modifier.then(if (highlight) Modifier.border(2.dp, Taxi, RoundedCornerShape(5.dp)) else Modifier).background(SignBg, RoundedCornerShape(5.dp)).padding(horizontal=9.dp,vertical=2.dp)) { Text(r.id + (side ?: ""),color=SignFg,fontWeight=FontWeight.ExtraBold) }
        Text(r.pair.replace(" ",""),color=if (highlight) Taxi else Dim,fontSize=12.sp)
    }
    if (stacked) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { content() }
}
@Composable private fun Metric(label: String, value: String) { Column { Text(label,color=Dim,fontSize=11.sp); Text(value,fontWeight=FontWeight.SemiBold) } }

@Composable private fun WindCard(s: UiState, lang: String) {
    val m = s.metar
    if (!s.metarLoaded) EmptyText(t(lang,"loading"))
    else if (s.metarError) EmptyText(t(lang,"windUnavailable"))
    else if (m == null) EmptyText(t(lang,"noMetar"))
    else {
        val fav = favouredRunway(m.dir?.toDouble(), m.speed?.toDouble())
        Text(if (fav != null) t(lang,"wind_fav","dir" to pad3(m.dir?.toDouble()),"spd" to (m.speed ?: 0).toString(),"rwy" to fav.name) else t(lang,"calm"))
        Text((m.obsMs?.let { t(lang,"obs_at","time" to fmtTime(it, false, lang)) } ?: t(lang,"obs_none")) + m.raw, color=Dim, fontFamily=FontFamily.Monospace, fontSize=12.sp, modifier=Modifier.padding(top=4.dp))
    }
}

@Composable private fun ForecastCard(r: ForecastRow, switched: Boolean, lang: String, modifier: Modifier = Modifier) {
    val (icon, cond) = weather(r.code, r.isDay != 0, lang)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(if (r.isNow) 2.dp else 1.dp, if(r.isNow) Taxi else Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(r.isNow) t(lang,"now") else r.clock, color=if(r.isNow) Taxi else PaintColor, fontWeight=if(r.isNow) FontWeight.Bold else FontWeight.SemiBold)
                Text("$icon  $cond", modifier=Modifier.weight(1f).padding(start=12.dp))
                Text(if(r.temp == null) "–" else "${nf(r.temp,0,lang)}°C", fontWeight=FontWeight.SemiBold)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("☔ ${r.pop?.let { if (lang == "fr") "$it\u00a0%" else "$it%" } ?: "–"}")
                    Text("👁️ ${r.visKm?.let { if(it>=10) "10+" else nf(it,1,lang) } ?: "–"} km")
                }
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("💨 ${if(r.dir==null||r.speed==null) "–" else "${pad3(r.dir)}° / ${r.speed.roundToInt()} kt"}")
                    Text("🌬️ ${t(lang, "gusts")} ${r.gust?.roundToInt() ?: "–"} kt")
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("🛬 ", fontSize=16.sp); if(r.fav!=null) { if(switched) Text("🔄 "); RunwayBadge(r.fav, switched) } else Text(t(lang,"lightWind"),color=Dim) }
        }
    }
}

@Composable private fun Controls(s: UiState, vm: YulViewModel, lang: String) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) {
        HorizontalDivider(color=Line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(t(lang,"refreshEvery"), color=Dim, modifier=Modifier.weight(1f))
            Dropdown(if (s.intervalSec == 120) "2 min" else s.intervalSec.toString()+" s", listOf(15,30,60,120), { if (it == 120) "2 min" else "$it s" }) { vm.setInterval(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Text(t(lang,"auto"),color=Dim); Switch(s.autoRefresh, vm::setAuto) }
        Button(onClick=vm::refresh, enabled=!s.busy, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(containerColor=Panel,contentColor=PaintColor)) { Text(t(lang,"refresh")) }
        if(s.autoRefresh) Text(if(s.busy) t(lang,"updating") else t(lang,"statusNext","s" to max(0,ceil((s.nextAt-System.currentTimeMillis())/1000.0).toInt()).toString()),color=Dim,fontSize=13.sp)
    }
}
@Composable private fun <T> Dropdown(current: String, values: List<T>, label: (T)->String, onSelect: (T)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={open=true}) { Text(current) }; DropdownMenu(expanded=open,onDismissRequest={open=false}) { values.forEach { DropdownMenuItem(text={Text(label(it))},onClick={open=false;onSelect(it)}) } } }
}
