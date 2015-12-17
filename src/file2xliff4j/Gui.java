/*
 * Gui.java
 *
 * Copyright (C) 2007. Lingotek, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  
 * 02110-1301, USA
 */

package file2xliff4j;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.charset.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import com.artofsolving.jodconverter.DocumentConverter;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.OpenOfficeDocumentConverter;
import com.artofsolving.jodconverter.DefaultDocumentFormatRegistry;


/**
 * @author Weldon Whipple &lt;weldon@whipple.org&gt;
 */

public class Gui extends JFrame {
    
    String[] languages = {"[Select language]", 
        "Abkhaz, Georgia (ab_GE)",
        "Afar, Djbouti (aa_DJ)",
        "Afrikaans, South Africa (af_ZA)",
        "Akan Asante, Ghana (ak_GH)",
        "Akan Fanti, Ghana (fat_GH)",
        "Akan Twi, Ghana (tw_GH)",
        "Albanian, Albania (sq_SQ)",
        "Amharic, Ethiopia (am_ET)",
        "Apache, United States (apa_US)",
        "Arabic, Afghanistan (ar_AF)",
        "Arabic, Algeria (ar_DZ)",
        "Arabic, Bahrain (ar_BH)",
        "Arabic, Chad (ar_TD)",
        "Arabic, Egypt (ar_EG)",
        "Arabic, Iraq (ar_IQ)",
        "Arabic, Jordan (ar_JO)",
        "Arabic, Libya (ar_LY)",
        "Arabic, Mauritania (ar_MR)",
        "Arabic, Morocco (ar_MA)",
        "Arabic, Oman (ar_OM)",
        "Arabic, Saudi Arabia (ar_SA)",
        "Arabic, STANDARD (ar)",
        "Arabic, Sudan (ar_SD)",
        "Arabic, Syria (ar_SY)",
        "Arabic, Tunisia (ar_TN)",
        "Arabic, United Arab Emirates (ar_AE)",
        "Arabic, Uzbekistan (ar_UZ)",
        "Arabic, Yemen (ar_YE)",
        "Armenian, Armenia (hy_AM)",
        "Assamese, India (as_IN)",
        "Asturian, Spain (ast_ES)",
        "Aymara, Bolivia (ay_BO)",
        "Azerbaijani, Azerbaijan (az_AZ)",
        "Bambara, Mali (bm_ML)",
        "Bashkir, Russia (ba_RU)",
        "Basque, Spain (eu_ES)",
        "Belarusian, Belarus (be_BY)",
        "Bengali, Bangladesh (bn_BD)",
        "Bikol / Bicolano, Philippines (bik_PH)",
        "Bisayan / Cebuano, Philippines (ceb_PH)",
        "Bislama, Vanuatu (bi_VU)",
        "Bosnian, Bosnia (bs_BA)",
        "Breton, France (br_FR)",
        "Bulgarian, Bulgaria (bg_BG)",
        "Burmese, Myanmar (my_MM)",
        "Cambodian / Khmer, Cambodia (km_KH)",
        "Catalan, Spain (ca_ES)",
        "Cebuano / Bisayan, Philippines (ceb_PH)",
        "Chamorro, Guam (ch_GU)",
        "Chechen, Russia (ce_RU)",
        "Cherokee, United States (chr_US)",
        "Cheyenne, United States (chy_US)",
        "Chichewa / Nyanja, Malawi (ny_MW)",
        "Chinese, China (zh_CN)",
        "Chinese, Hong Kong (zh_HK)",
        "Chinese, Singapore (zh_SG)",
        "Chinese, Taiwan (zh_TW)",
        "Cornish, United Kingdom (kw_GB)",
        "Corsican, France (co_FR)",
        "Croatian, Croatia (hr_HR)",
        "Czech, Czech Republic (cs_CZ)",
        "Danish, Denmark (da_DK)",
        "Dari, Iran (gbz_IR)",
        "Divehi, Maldives (dv_MV)",
        "Dutch, Netherlands (nl_NL)",
        "Dzongkha, Bhutan (dz_BT)",
        "Efik, Nigeria (efi_NG)",
        "English, Australia (en_AU)",
        "English, Canada (en_CA)",
        "English Creoles and Pidgins (cpe_US)",
        "English, South Africa (en_ZA)",
        "English, United Kingdom (en_GB)",
        "English, United States (en_US)",
        "Esperanto, France (eo_FR)",
        "Estonian, Estonia (et_EE)",
        "Ewe, Ghana (ee_GH)",
        "Fanti (Akan) Ghana (fat_GH)",
        "Farsi (Persian) Iran (fa_IR)",
        "Fijian, Fiji (fj_FJ)",
        "Fijian, India (fj_IN)",
        "Finnish, Finland (fi_FI)",
        "Fon, Benin (fon_BJ)",
        "French, Cajun (fr_US)",
        "French, Canadian (fr_CA)",
        "French Creoles and Pidgins (cpf_MU)",
        "French, France (fr_FR)",
        "Gaelic, Irish (ga_IE)",
        "Gaelic, Scottish (gd_GB)",
        "Ga, Ghana (gaa_GH)",
        "Galician, Spain (gl_ES)",
        "Ganda, Uganda (lg_UG)",
        "Georgian, Georgia (ka_GE)",
        "German, Germany (de_DE)",
        "Gikuyu / Kikuyu, Kenya (kik_KE)",
        "Gilbertese, Kiribati (gil_KI)",
        "Greek, Greece (el_GR)",
        "Guarani, Bolivia (gn_BO)",
        "Gujarati, India (gu_IN)",
        "Haitian Creole, Haiti (ht_HT)",
        "Hausa, Nigeria (ha_NG)",
        "Hawaiian, United States (haw_US)",
        "Hebrew, Israel (he_IL)",
        "Hiligaynon / Ilonngo, Philippines (hil_PH)",
        "Hindi, India (hi_IN)",
        "Hmong, Laos (hmn_LA)",
        "Hungarian, Hungary (hu_HU)",
        "Icelandic, Iceland (is_IS)",
        "Igbo, Nigeria (ig_NG)",
        "Iloko / Ilocano, Philippines (Ilo_PH)",
        "Ilonngo / Hiligaynon, Philippines (hil_PH)",
        "Indonesian, Indonesia (id_ID)",
        "Italian, Italy (it_IT)",
        "Japanese, Japan (ja_JP)",
        "Javanese, Indonesia (jv_ID)",
        "Kannada, India (kn_IN)",
        "Kashmiri, India (ks_IN)",
        "Kazakh, Kazakhstan (kk_KZ)",
        "Kekchi&apos; / Q&apos;eqchi&apos;, Guatemala (kek_GT)",
        "Khmer / Cambodian, Cambodia (km_KH)",
        "Kikongo / Kongo, Congo (kg_CD)",
        "Kikuyu / Gikuyu, Kenya (kik_KE)",
        "Kinyarwanda / Rwanda, Rwanda (kin_RW)",
        "Kirghiz, Kyrgyzstan (ky_KG)",
        "Kongo / Kikongo, Congo (kg_CD)",
        "Korean, Korea (ko_KR)",
        "Kosraean, Micronesia (kos_FM)",
        "Kurdish, Iraq (ku_IQ)",
        "Kwanyama, Angola (kj_AO)",
        "Lao, Laos (lo_LA)",
        "Latin, Vatican City (la_VA)",
        "Latvian, Latvia (lv_LV)",
        "Lingala, Congo (ln_CD)",
        "Lithuanian, Lithuania (lt_LT)",
        "Luba_Katanga, Congo (lu_CD)",
        "Luxembourgeois, Luxembourg (lb_LU)",
        "Macedonian, Macedonia (mk_MK)",
        "Malagasy, Madagascar (mg_MG)",
        "Malayalam, India (ml_IN)",
        "Malay Bahasa Melayu, Malaysia (ms_MY)",
        "Maltese, Malta (mt_MT)",
        "Maori, New Zealand (mi_NZ)",
        "Marathi, India (mr_IN)",
        "Marshallese, Marshal Islands (mh_MH)",
        "Mauritian Creole, Mauritius (cpf_MU)",
        "Moldavian, Moldova (mo_MD)",
        "Mongolian, Mongolia (mn_MN)",
        "Nauruan, Nauru (na_NR)",
        "Navajo, United States (nv_US)",
        "Ndebele, North, Zimbabwe (nd_ZW)",
        "Ndebele, South, South Africa (nr_ZA)",
        "Ndonga, Nambia (ng_NA)",
        "Nepali, Nepal (ne_NP)",
        "Niuean, Niue (niu_NU)",
        "Norwegian Bokmal, Norway (nb_NO)",
        "Norwegian, Norway (no_NO)",
        "Norwegian Nynorsk, Norway (nn_NO)",
        "Nyanja / Chichewa, Malawi (ny_MW)",
        "Oriya, India (or_IN)",
        "Oromo, Ethiopia (om_ET)",
        "Palauan, Palau (pau_PW)",
        "Pangasinan, Philippines (pag_PH)",
        "Papiamentu, Netherland Antilles (pap_AN)",
        "Persian Farsi, Iran (fa_IR)",
        "Polish, Poland (pl_PL)",
        "Portuguese, Brazil (pt_BR)",
        "Portuguese Creoles and Pidgins (cpp_BR)",
        "Portuguese, Portugal (pt_PT)",
        "Punjabi, Indian script (pa_IN)",
        "Punjabi, Latin script (pa_PK)",
        "Pushto / Pashto, Afghanistan (ps_AF)",
        "Q&apos;eqchi&apos; / Kekchi&apos;, Guatemala (kek_GT)",
        "Quechua, Bolivian (qu_BO)",
        "Quechua, Peru (qu_Pe)",
        "Rarotongan, Cook Islands (rar_CK)",
        "Romanian, Romania (ro_RO)",
        "Rundi, Burundi (rn_BI)",
        "Russian, Russia (ru_RU)",
        "Rwanda / Kinyarwanda, Rwanda (kin_RW)",
        "Samoan, Samoa (sm_WS)",
        "Sango, Central African Rep. (sg_CF)",
        "Sanskrit, India (sa_IN)",
        "Sardinian, Italy (sc_IT)",
        "Serbian, Serbia (sr_CS)",
        "Shona, Zimbabwe (sn_ZW)",
        "Sicilian, Italy (scn_IT)",
        "Sindhi, Pakistan (sd_PK)",
        "Sinhala, Sri Lanka (si_LK)",
        "Slovak, Slovakia (sk_SK)",
        "Slovenian, Slovenia (sl_SI)",
        "Somali, Somalia (so_SO)",
        "Sotho Northern, South Africa (nso_ZA)",
        "Sotho Southern, Lesotho (st_LS)",
        "Spanish, Argentina (es_AR)",
        "Spanish, Bolivia (es_BO)",
        "Spanish, Chile (es_CL)",
        "Spanish, Colombia (es_CO)",
        "Spanish, Costa Rica (es_CR)",
        "Spanish, Cuba (es_CU)",
        "Spanish, Dominican Republic (es_DO)",
        "Spanish, Ecuador (es_EC)",
        "Spanish, El Salvador (es_SV)",
        "Spanish, Guatemala (es_GT)",
        "Spanish, Honduras (es_HN)",
        "Spanish, Mexico (es_MX)",
        "Spanish, Nicaragua (es_NI)",
        "Spanish, Panama (es_PA)",
        "Spanish, Paraguay (es_PY)",
        "Spanish, Peru (es_PE)",
        "Spanish, Puerto Rico (es_PR)",
        "Spanish, Spain (es_ES)",
        "Spanish, Unites States (es_US)",
        "Spanish, Uruguay (es_UY)",
        "Spanish, Venezuela (es_VE)",
        "Sunda / Sundanese (su_ID)",
        "Swahili, Tanzania (sw_TZ)",
        "Swati, Swaziland (ss_SZ)",
        "Swedish, Sweden (sv_SE)",
        "Tagalog, Philippines (tl_PH)",
        "Tahitian, French Polynesia (ty_PF)",
        "Tajiki, Tajikistan (tg_TJ)",
        "Tamil, India (ta_IN)",
        "Telugu, India (te_IN)",
        "Thai, Thailand (th_TH)",
        "Tibetan, China (bo_CN)",
        "Tigrigna / Tigrinya, Eritrea (ti_ER)",
        "Tok Pisin, Papua New Guinea (tpi_PG)",
        "Tongan, Tonga (to_TO)",
        "Tsonga, South Africa (ts_ZA)",
        "Tswana, Botswana (tn_BW)",
        "Tumbuku, Malawi (tum_MW)",
        "Turkish, Turkey (tr_TR)",
        "Turkmen, Turkmenistan (tk_TM)",
        "Tuvaluan, Tuvalu (tvl_TV)",
        "Twi (Akan), Ghana (tw_GH)",
        "Ukrainian, Ukraine (uk_UA)",
        "Umbundu, Angola (um_AO)",
        "Urdu, Pakistan (ur_PK)",
        "Uyghur / Uighur, China (ug_CN)",
        "Uzbek, Uzbekistan (uz_UZ)",
        "Venda, South Africa (ve_ZA)",
        "Vietnamese, Viet Nam (vi_VN)",
        "Waray, Philippines (war_PH)",
        "Welsh, United Kingdom (cy_GB)",
        "Wolof, Senegal (wo_SN)",
        "Xhosa, South Africa (xh_ZA)",
        "Yapese, Micronesia (yap_FM)",
        "Yiddish, Israel (yi_IL)",
        "Yoruba, Nigeria (yo_NG)",
        "Zulu, South Africa (zu_ZA)"
    };
    
    String[] encodings = {"[Select encoding]",
        "UTF-8",
        "Big5",
        "Big5-HKSCS",
        "EUC-JP",
        "EUC-KR",
        "GB18030",
        "GB2312",
        "GBK",
        "IBM-Thai",
        "IBM00858",
        "IBM01140",
        "IBM01141",
        "IBM01142",
        "IBM01143",
        "IBM01144",
        "IBM01145",
        "IBM01146",
        "IBM01147",
        "IBM01148",
        "IBM01149",
        "IBM037",
        "IBM1026",
        "IBM1047",
        "IBM273",
        "IBM277",
        "IBM278",
        "IBM280",
        "IBM284",
        "IBM285",
        "IBM297",
        "IBM420",
        "IBM424",
        "IBM437",
        "IBM500",
        "IBM775",
        "IBM850",
        "IBM852",
        "IBM855",
        "IBM857",
        "IBM860",
        "IBM861",
        "IBM862",
        "IBM863",
        "IBM864",
        "IBM865",
        "IBM866",
        "IBM868",
        "IBM869",
        "IBM870",
        "IBM871",
        "IBM918",
        "ISO-2022-CN",
        "ISO-2022-JP",
        "ISO-2022-KR",
        "ISO-8859-1",
        "ISO-8859-13",
        "ISO-8859-15",
        "ISO-8859-2",
        "ISO-8859-3",
        "ISO-8859-4",
        "ISO-8859-5",
        "ISO-8859-6",
        "ISO-8859-7",
        "ISO-8859-8",
        "ISO-8859-9",
        "JIS_X0201",
        "JIS_X0212-1990",
        "KOI8-R",
        "Shift_JIS",
        "TIS-620",
        "US-ASCII",
        "UTF-16",
        "UTF-16BE",
        "UTF-16LE",
        "windows-1250",
        "windows-1251",
        "windows-1252",
        "windows-1253",
        "windows-1254",
        "windows-1255",
        "windows-1256",
        "windows-1257",
        "windows-1258",
        "windows-31j",
        "x-Big5-Solaris",
        "x-euc-jp-linux",
        "x-EUC-TW",
        "x-eucJP-Open",
        "x-IBM1006",
        "x-IBM1025",
        "x-IBM1046",
        "x-IBM1097",
        "x-IBM1098",
        "x-IBM1112",
        "x-IBM1122",
        "x-IBM1123",
        "x-IBM1124",
        "x-IBM1381",
        "x-IBM1383",
        "x-IBM33722",
        "x-IBM737",
        "x-IBM856",
        "x-IBM874",
        "x-IBM875",
        "x-IBM921",
        "x-IBM922",
        "x-IBM930",
        "x-IBM933",
        "x-IBM935",
        "x-IBM937",
        "x-IBM939",
        "x-IBM942",
        "x-IBM942C",
        "x-IBM943",
        "x-IBM943C",
        "x-IBM948",
        "x-IBM949",
        "x-IBM949C",
        "x-IBM950",
        "x-IBM964",
        "x-IBM970",
        "x-ISCII91",
        "x-ISO-2022-CN-CNS",
        "x-ISO-2022-CN-GB",
        "x-iso-8859-11",
        "x-JIS0208",
        "x-JISAutoDetect",
        "x-Johab",
        "x-MacArabic",
        "x-MacCentralEurope",
        "x-MacCroatian",
        "x-MacCyrillic",
        "x-MacDingbat",
        "x-MacGreek",
        "x-MacHebrew",
        "x-MacIceland",
        "x-MacRoman",
        "x-MacRomania",
        "x-MacSymbol",
        "x-MacThai",
        "x-MacTurkish",
        "x-MacUkraine",
        "x-MS950-HKSCS",
        "x-mswin-936",
        "x-PCK",
        "x-windows-874",
        "x-windows-949",
        "x-windows-950"
    };

    String[] types = {"[Select filetype]",
        "Word document (*.doc)",
        "Web page (*.html; *.htm)",
        "Excel spreadsheet (*.xls)",
        "PowerPoint presentation (*.ppt)",
        "OpenOffice.org text (*.odt)",
        "OpenOffice.org spreadsheet (*.ods)",
        "OpenOffice.org presentation (*.odp)",
        "Rich Text Format document (*.rtf)",
        "Maker Interchange Format document (*.mif)",
        "Extensible Markup Language document (*.xml)",
        "Plaintext document (*.txt)",
        "Portable Document Format document (*.pdf)",
        "Java property resource bundle (*.properties)",
        "XML User Interface Language DTD (*.dtd)",
        "GNU Portable Object [Template] (*.po, *.pot)",
        "Windows resource file (*.rc)"
    };

    private String filename;
    private String basedir;
    private ConversionMode mode = ConversionMode.TO_XLIFF;
    private Locale lang;
    private Charset encoding;
    private FileType filetype;
    
    // For a status message.
    JLabel  jlabMessage;

     // Labels (go to left of text fields)
    JLabel  jlabNativeFileName;
    JLabel  jlabBaseDir;
    
    // To XLIFF: "Language of native document:"; From XLIFF: "Language to export:"
    JLabel  jlabLanguage;
    JLabel  jlabFileType;
    JLabel  jlabSourceEncoding;
    
    // Text input fields
    JTextField jtfNativeFileName;
    JTextField jtfBaseDir;

    // Buttons for browsing to find files
    JButton jbtnBrowseNativeFile;
    
    // Radio Buttons for to choose between toXliff and fromXliff
    JRadioButton jrbToXliff;
    JRadioButton jrbFromXliff;
    
    // Combo boxes (dropdowns) for choosing languages, encodings, filetypes
    JComboBox jcbbLanguage;
    JComboBox jcbbSourceEncoding;
    JComboBox jcbbFileType;
    
    // Buttons to make us quit or convert.
    JButton jbtnConvert;
    JButton jbtnQuit;
    
    JFileChooser jfc;     // For choosing file names
    JFrame chooserFrame;
    
    public Gui() {
        super("File2XLIFF Converter");
        
        Container cp = getContentPane();
        GridBagLayout gbag = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        cp.setLayout(gbag);
        
        
        // Set the size of the frame
        setSize(240, 240);
        
        jfc = new JFileChooser();

        // Let's work with each of the controls:
        // How about a status message line:
        jlabMessage = new JLabel("Please complete the fields below.", SwingConstants.LEFT);
        
         // Labels (go to left of text fields)
        jlabBaseDir = new JLabel("Base directory:", SwingConstants.RIGHT);
        jlabNativeFileName = new JLabel("Original file name:", SwingConstants.RIGHT);

        // Text input fields
        jtfBaseDir = new JTextField(39);
        jtfNativeFileName = new JTextField(30);

        // Buttons for browsing to find files
        jbtnBrowseNativeFile = new JButton("Browse...");

        // Radio Buttons for to choose between toXliff and fromXliff
        jrbToXliff = new JRadioButton("Convert to XLIFF", true);  // Select this one initially
        jrbFromXliff = new JRadioButton("Convert from XLIFF");
        
        // Add radio buttons to a group:
        ButtonGroup bg = new ButtonGroup();
        bg.add(jrbToXliff);
        bg.add(jrbFromXliff);

        // To XLIFF: "Language of native document:"; From XLIFF: "Language to export:"
        jlabLanguage = new JLabel("Language of original document:", SwingConstants.RIGHT);

        jlabFileType = new JLabel("File type of original document:", SwingConstants.RIGHT);
        jlabSourceEncoding = new JLabel("Encoding of original document:", SwingConstants.RIGHT);
        
        // Combo boxes (dropdowns) for choosing languages, encodings, filetypes
        jcbbLanguage       = new JComboBox(languages);
        jcbbSourceEncoding = new JComboBox(encodings);
        jcbbFileType       = new JComboBox(types);

        // Buttons to make us quit or convert.
        jbtnConvert = new JButton("Convert");
        jbtnQuit    = new JButton("Quit");

        // Define the grid bag:
        gbc.weightx = 1.0;
        Insets regularInsets = new Insets(4,4,4,4);
        Insets spaceInsets = new Insets(10,4,4,4);
        gbc.insets = regularInsets;

        // Let's put the message line at top:
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jlabMessage, gbc);
        
        // Set constraints on the first row of controls
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbag.setConstraints(jlabNativeFileName, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jtfNativeFileName, gbc);
        
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jbtnBrowseNativeFile, gbc);

        // ... and the second
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbag.setConstraints(jlabBaseDir, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jtfBaseDir, gbc);
        
        // The third rows has the toXLIFF/fromXliff radio button group
        gbc.insets = spaceInsets;
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = spaceInsets;
        gbag.setConstraints(jrbToXliff, gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jrbFromXliff, gbc);
        
        // The fourth row is for locale
        gbc.insets = regularInsets;
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        gbag.setConstraints(jlabLanguage, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jcbbLanguage, gbc);
        
        // Fifth row is filetype
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbag.setConstraints(jlabFileType, gbc);

        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jcbbFileType, gbc);
        
        // Sixth row is encoding
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbag.setConstraints(jlabSourceEncoding, gbc);

        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jcbbSourceEncoding, gbc);
        
        // The seventh is the bottom row

        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbag.setConstraints(jbtnConvert, gbc);
        
        gbc.gridx = 2;
        gbc.gridy = 7;
        gbc.anchor = GridBagConstraints.WEST;
        gbag.setConstraints(jbtnQuit, gbc);
        
        cp.add(jlabMessage);
        
        cp.add(jlabBaseDir);
        cp.add(jlabNativeFileName);
        cp.add(jtfBaseDir);   
        cp.add(jtfNativeFileName);
        cp.add(jbtnBrowseNativeFile);

        cp.add(jrbToXliff);
        cp.add(jrbFromXliff);
        
        cp.add(jlabLanguage);   
        cp.add(jcbbLanguage);
        
        cp.add(jlabFileType);
        cp.add(jcbbFileType);

        cp.add(jlabSourceEncoding);
        cp.add(jcbbSourceEncoding);   

        cp.add(jbtnConvert);
        cp.add(jbtnQuit);
        
        // Set up that close will exit the program,
        // not just close the JFrame
        // What to do on close: exit
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocation(200,200);
        
        final JFrame chooserFrame = new JFrame("Select native file");

        // Handle Browse button clicks
        jbtnBrowseNativeFile.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int returnVal = jfc.showOpenDialog(chooserFrame);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    String fullPath = jfc.getSelectedFile().getPath();

                    // Look first for a WinDoze-style directory separator.
                    char separatorChar = '\\';   // Default to WinDoze style
                    int lastDirCharPos = fullPath.lastIndexOf("\\");
                    if (lastDirCharPos == -1) {
                        // If no WinDoze-style separator found, look for a *nix separator
                        lastDirCharPos = fullPath.lastIndexOf("/");
                        separatorChar = '/';     // *nix/OS X style
                    }

                    if ((lastDirCharPos >= 0) && (lastDirCharPos < (fullPath.length() - 1))) {
                        filename = fullPath.substring(lastDirCharPos + 1);
                        basedir = fullPath.substring(0, lastDirCharPos);
                        jtfNativeFileName.setText(filename);
                        jtfBaseDir.setText(basedir);
                    }
                }
            }
        });
        
        // Handle case where user keys in file name (without using the
        // file chooser).
        jtfNativeFileName.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                filename = jtfNativeFileName.getText();
            }
        });
        
        // Ditto for basedir
        jtfBaseDir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                basedir = jtfBaseDir.getText();
            }
        });
        
        // Handle click of Convert to XLIFF radio button
        jrbToXliff.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mode = ConversionMode.TO_XLIFF;
                jlabLanguage.setText("Language of original document:");
            }
        });
        
        // Handle click of Convert from XLIFF radio button
        jrbFromXliff.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mode = ConversionMode.FROM_XLIFF;
                jlabLanguage.setText("Language to export:");
            }
        });
        
        // This one handles the source language combo box.
        jcbbLanguage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String language = (String) jcbbLanguage.getSelectedItem();
                lang = strToLocale(language);
            }
        });

        // Handles the original filetype combo box.
        jcbbFileType.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = (String) jcbbFileType.getSelectedItem();
                if (type.contains("(*.doc)")) { filetype = FileType.WORD; }
                else if (type.contains("(*.html"))  { filetype = FileType.HTML; }
                else if (type.contains("(*.xls)"))  { filetype = FileType.EXCEL; }
                else if (type.contains("(*.ppt)"))  { filetype = FileType.PPT; }
                else if (type.contains("(*.odt)"))  { filetype = FileType.ODT; }
                else if (type.contains("(*.ods)"))  { filetype = FileType.ODS; }
                else if (type.contains("(*.odp)"))  { filetype = FileType.ODP; }
                else if (type.contains("(*.rtf)"))  { filetype = FileType.RTF; }
                else if (type.contains("(*.mif)"))  { filetype = FileType.MIF; }
                else if (type.contains("(*.xml)"))  { filetype = FileType.XML; }
                else if (type.contains("(*.txt)"))  { filetype = FileType.PLAINTEXT; }
                else if (type.contains("(*.pdf)"))  { filetype = FileType.PDF; }
                else if (type.contains("(*.properties)"))  { filetype = FileType.JAVA_PROPERTIES; }
                else if (type.contains("(*.dtd)"))  { filetype = FileType.XULDTD; }
                else if (type.contains("(*.po, *.pot)")) { filetype = FileType.PO; }
                else if (type.contains("(*.rc)"))   { filetype = FileType.WINRC; }
            }
        });

        // Handle the source encoding combo box.
        jcbbSourceEncoding.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String sourceEncoding = (String) jcbbSourceEncoding.getSelectedItem();
                if (!sourceEncoding.startsWith("[")) {
                    encoding = Charset.forName(sourceEncoding);
                }
                else {
                    encoding = null;
                }
            }
        });

        // Handle click of the the Convert button
        jbtnConvert.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Re-get the filename and basedir fields in case they changes
                // without generating an action.
                filename = jtfNativeFileName.getText();
                basedir  = jtfBaseDir.getText();
                String results = validateInput();
                if (results.length() > 0) {
                    jlabMessage.setText(results);
                }
                else {
                    Converter converter = null;
                    if (mode.equals(ConversionMode.TO_XLIFF)) {
                        try {
                            converter = ConverterFactory.createConverter(filetype, FileType.XLIFF);
                        }
                        catch(ConversionException ce) {
                            jlabMessage.setText(ce.getMessage());
                            return;
                        }
                    }
                    else if (mode.equals(ConversionMode.FROM_XLIFF)) {
                        try {
                            converter = ConverterFactory.createConverter(FileType.XLIFF, filetype);
                        }
                        catch(ConversionException ce) {
                            jlabMessage.setText(ce.getMessage());
                            return;
                        }
                    }

                    // Now invoke the converter:
                    jlabMessage.setText("Conversion underway ...");
                    try {
                        converter.convert(mode, lang, null, 0, encoding, filetype, filename,
                                basedir, null, SegmentBoundary.SENTENCE, null);
                    }
                    catch(ConversionException ce) {
                        String errorMsg = ce.getMessage();
                        if (errorMsg.length() > 81) {
                            errorMsg = errorMsg.substring(0, 81) + "...";
                        }
                        jlabMessage.setText(errorMsg);
                        return;
                    }

                    jlabMessage.setText("Conversion complete.");
                }
                return;
            }
        });
        
        // Handle click of the the Quit button
        jbtnQuit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                dispose();
                System.exit(0);
            }
        });
        
        pack();
        
    }
    
    /**
     * Use the GUI to convert.
     */
    public void convert() {
        // Create the GUI (variable is final because used by inner class).
        final JFrame jfF2X = new Gui();
        
        //Create a Runnable to set the main visible, and get Swing to invoke.
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                jfF2X.setVisible(true);
            }
        });
        
        return;
    }
    
    /**
     * Main method (for testing, for example)
     */
    public static void main(String[] args) {
        // Create the GUI (variable is final because used by inner class).
        final JFrame jfF2X = new Gui();
        
        //Create a Runnable to set the main visible, and get Swing to invoke.
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                jfF2X.setVisible(true);
            }
        });
    }
    
    /**
     * Passed a string representing a language, convert it to a Locale
     * @param lang The string representing the language
     * @return the Locale that corresponds to the lang string
     */
    private Locale strToLocale(String lang) {
        if (lang == null || lang.trim().length() == 0 || lang.startsWith("[")) {
            return null;    // No language selected
        }
        
        // Implied else:
        // The lang string will be an English name of the language, followed (in parentheses)
        // by an ISO language code. We are interested in the language code in parens.
        Matcher langMatcher = Pattern.compile("[(]([^)]+)[)]", Pattern.DOTALL).matcher("");
        
        String langCode = null;
        
        langMatcher.reset(lang);
        if (langMatcher.find()) {
            langCode = langMatcher.group(1);
        }
        
        Locale locale = null;
        
        // Break the langCode into its component parts (which are separated by zero,
        // one or two underscores).
        if (langCode != null) {
            String[] split = langCode.split("_");
            if (split.length == 3) {
                locale = new Locale(split[0], split[1], split[2]);
            }
            else if (split.length == 2) {
                locale = new Locale(split[0], split[1]);
            }
            else {
                locale = new Locale(lang);
            }
        }

        return locale;
    }

    /**
     * Validate input fields, from top to bottom, left to right. When the first
     * invalid entry is encountered, return a string describing the problem.
     * If no errors are detected, return a zero-length string.
     * @return Error message or no message (if no errors)
     */
    private String validateInput() {

        if (filename == null || filename.trim().length() == 0) {
            return "Specify the original file name.";
        }

        // Validate the source file
        if (basedir == null || basedir.trim().length() == 0) {
            return "Specify the base directory.";
        }

        File origFile = new File(basedir + File.separator + filename);
        if (!origFile.exists()) {
            return "The original file you specified does not exist. Please re-specify.";
        }
        
        // Verify that the language is specified
        if (lang == null) {
            return "Specify the language.";
        }
        
        // Verify the file type (for conversion to XLIFF)
        if (mode.equals(ConversionMode.TO_XLIFF)) {
            if (filetype == null) {
                return "Specify the file type.";
            }
        }

        // Check the encoding
        if (encoding == null) {
            encoding = Charset.forName("UTF-8");
        }
        
        return "";    // All is OK
    }    
}