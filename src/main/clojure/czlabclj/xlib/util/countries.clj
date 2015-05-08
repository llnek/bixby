;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc "A class that maps country-codes to the country-names."
      :author "kenl" }

  czlabclj.xlib.util.countries

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cstr ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _CCODES {
    "AF"  "Afghanistan"
    "AL"  "Albania"
    "DZ"  "Algeria"
    "AS"  "American Samoa"
    "AD"  "Andorra"
    "AO"  "Angola"
    "AI"  "Anguilla"
    "AQ"  "Antarctica"
    "AG"  "Antigua and Barbuda"
    "AR"  "Argentina"
    "AM"  "Armenia"
    "AW"  "Aruba"
    "AU"  "Australia"
    "AT"  "Austria"
    "AZ"  "Azerbaijan"
    "BS"  "Bahamas"
    "BH"  "Bahrain"
    "BD"  "Bangladesh"
    "BB"  "Barbados"
    "BY"  "Belarus"
    "BE"  "Belgium"
    "BZ"  "Belize"
    "BJ"  "Benin"
    "BM"  "Bermuda"
    "BT"  "Bhutan"
    "BO"  "Bolivia"
    "BA"  "Bosnia and Herzegowina"
    "BW"  "Botswana"
    "BV"  "Bouvet Island"
    "BR"  "Brazil"
    "VG"  "Virgin Islands (British)"
    "IO"  "British Indian Ocean Territory"
    "BN"  "Brunei Darussalam"
    "BG"  "Bulgaria"
    "BF"  "Burkina Faso"
    "BI"  "Burundi"
    "KH"  "Cambodia"
    "CM"  "Cameroon"
    "CA"  "Canada"
    "CV"  "Cape Verde"
    "KY"  "Cayman Islands"
    "CF"  "Central African Republic"
    "TD"  "Chad"
    "CL"  "Chile"
    "CN"  "China"
    "CX"  "Christmas Island"
    "CC"  "Cocos (Keeling) Islands"
    "CO"  "Colombia"
    "KM"  "Comoros"
    "CG"  "Congo"
    "CD"  "Congo Democratic Republic"
    "CK"  "Cook Islands"
    "CR"  "Costa Rica"
    "CI"  "Cote D'Ivoire"
    "HR"  "Croatia"
    "CY"  "Cyprus"
    "CZ"  "Czech Republic"
    "DK"  "Denmark"
    "DJ"  "Djibouti"
    "DM"  "Dominica"
    "DO"  "Dominican Republic"
    "TP"  "East Timor"
    "EC"  "Ecuador"
    "EG"  "Egypt"
    "SV"  "El Salvador"
    "GQ"  "Equatorial Guinea"
    "ER"  "Eritrea"
    "EE"  "Estonia"
    "ET"  "Ethiopia"
    "FK"  "Falkland Islands (Malvinas)"
    "FO"  "Faroe Islands"
    "FJ"  "Fiji"
    "FI"  "Finland"
    "FR"  "France"
    "GF"  "French Guiana"
    "PF"  "French Polynesia"
    "TF"  "French Southern Territories"
    "GA"  "Gabon"
    "GM"  "Gambia"
    "GE"  "Georgia"
    "DE"  "Germany"
    "GH"  "Ghana"
    "GI"  "Gibraltar"
    "GR"  "Greece"
    "GL"  "Greenland"
    "GD"  "Grenada"
    "GP"  "Guadeloupe"
    "GU"  "Guam"
    "GT"  "Guatemala"
    "GN"  "Guinea"
    "GW"  "Guinea-Bissau"
    "GY"  "Guyana"
    "HT"  "Haiti"
    "HM"  "Heard and McDonald Islands"
    "HN"  "Honduras"
    "HK"  "Hong Kong"
    "HU"  "Hungary"
    "IS"  "Iceland"
    "IN"  "India"
    "ID"  "Indonesia"
    "IE"  "Ireland"
    "IL"  "Israel"
    "IT"  "Italy"
    "JM"  "Jamaica"
    "JP"  "Japan"
    "JO"  "Jordan"
    "KZ"  "Kazakhstan"
    "KE"  "Kenya"
    "KI"  "Kiribati"
    "KR"  "Korea - Republic of"
    "KW"  "Kuwait"
    "KG"  "Kyrgyzstan"
    "LA"  "Lao People's Democratic Republic"
    "LV"  "Latvia"
    "LB"  "Lebanon"
    "LS"  "Lesotho"
    "LR"  "Liberia"
    "LI"  "Liechtenstein"
    "LT"  "Lithuania"
    "LU"  "Luxembourg"
    "MO"  "Macau"
    "MK"  "Macedonia (former Yugoslav Rep.)"
    "MG"  "Madagascar"
    "MW"  "Malawi"
    "MY"  "Malaysia"
    "MV"  "Maldives"
    "ML"  "Mali"
    "MT"  "Malta"
    "MH"  "Marshall Islands"
    "MQ"  "Martinique"
    "MR"  "Mauritania"
    "MU"  "Mauritius"
    "YT"  "Mayotte"
    "MX"  "Mexico"
    "FM"  "Micronesia - Federated States of"
    "MD"  "Moldova - Republic of"
    "MC"  "Monaco"
    "MN"  "Mongolia"
    "MS"  "Montserrat"
    "MA"  "Morocco"
    "MZ"  "Mozambique"
    "MM"  "Myanmar"
    "NA"  "Namibia"
    "NR"  "Nauru"
    "NP"  "Nepal"
    "NL"  "Netherlands"
    "AN"  "Netherlands Antilles"
    "NC"  "New Caledonia"
    "NZ"  "New Zealand"
    "NI"  "Nicaragua"
    "NE"  "Niger"
    "NG"  "Nigeria"
    "NU"  "Niue"
    "NF"  "Norfolk Island"
    "MP"  "Northern Mariana Islands"
    "NO"  "Norway"
    "OM"  "Oman"
    "PK"  "Pakistan"
    "PW"  "Palau"
    "PS"  "Palestine"
    "PA"  "Panama"
    "PG"  "Papua New Guinea"
    "PY"  "Paraguay"
    "PE"  "Peru"
    "PH"  "Philippines"
    "PN"  "Pitcairn"
    "PL"  "Poland"
    "PT"  "Portugal"
    "PR"  "Puerto Rico"
    "QA"  "Qatar"
    "RE"  "Reunion"
    "RO"  "Romania"
    "RU"  "Russian Federation"
    "RW"  "Rwanda"
    "KN"  "Saint Kitts and Nevis"
    "LC"  "Saint Lucia"
    "VC"  "Saint Vincent and the Grenadines"
    "WS"  "Samoa"
    "SM"  "San Marino"
    "ST"  "Sao Tome and Principe"
    "SA"  "Saudi Arabia"
    "SN"  "Senegal"
    "CS"  "Serbia and Montenegro"
    "SC"  "Seychelles"
    "SL"  "Sierra Leone"
    "SG"  "Singapore"
    "SK"  "Slovakia (Slovak Republic)"
    "SI"  "Slovenia"
    "SB"  "Solomon Islands"
    "SO"  "Somalia"
    "ZA"  "South Africa"
    "ES"  "Spain"
    "LK"  "Sri Lanka"
    "SH"  "St. Helena"
    "PM"  "St. Pierre and Miquelon"
    "SR"  "Suriname"
    "SJ"  "Svalbard and Jan Mayen Islands"
    "SZ"  "Swaziland"
    "SE"  "Sweden"
    "CH"  "Switzerland"
    "TW"  "Taiwan"
    "TJ"  "Tajikistan"
    "TZ"  "Tanzania - United Republic of"
    "TH"  "Thailand"
    "TG"  "Togo"
    "TK"  "Tokelau"
    "TO"  "Tonga"
    "TT"  "Trinidad and Tobago"
    "TN"  "Tunisia"
    "TR"  "Turkey"
    "TM"  "Turkmenistan"
    "TC"  "Turks and Caicos Islands"
    "TV"  "Tuvalu"
    "UG"  "Uganda"
    "UA"  "Ukraine"
    "AE"  "United Arab Emirates"
    "GB"  "United Kingdom"
    "US"  "United States"
    "UM"  "United States Minor Outlying Islands"
    "UY"  "Uruguay"
    "UZ"  "Uzbekistan"
    "VU"  "Vanuatu"
    "VA"  "Vatican City State (Holy See)"
    "VE"  "Venezuela"
    "VN"  "Vietnam"
    "VI"  "Virgin Islands (U.S.)"
    "WF"  "Wallis And Futuna Islands"
    "EH"  "Western Sahara"
    "YE"  "Yemen"
    "YU"  "Yugoslavia"
    "ZM"  "Zambia"
    "ZW"  "Zimbabwe"
})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _CCODESEQ (seq _CCODES))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindCountry "Return the full country name."

  ^String
  [^String code]

  (_CCODES (cstr/upper-case code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListCodes"List all the country codes."

  []

  (keys _CCODES))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsUSA? "Returns true if the code is US."

  [^String code]

  (= "US" (cstr/upper-case code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindCountryCode "Return the country code."

  ^String
  [^String country]

  (when-let [rs (filter #(= (nth % 1) country) _CCODESEQ) ]
    (nth (first rs) 0)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _STATES {
    "AL"  "Alabama"
    "AK"  "Alaska"
    "AZ"  "Arizona"
    "AR"  "Arkansas"
    "CA"  "California"
    "CO"  "Colorado"
    "CT"  "Connecticut"
    "DE"  "Delaware"
    "FL"  "Florida"
    "GA"  "Georgia"
    "HI"  "Hawaii"
    "ID"  "Idaho"
    "IL"  "Illinois"
    "IN"  "Indiana"
    "IA"  "Iowa"
    "KS"  "Kansas"
    "KY"  "Kentucky"
    "LA"  "Louisiana"
    "ME"  "Maine"
    "MD"  "Maryland"
    "MA"  "Massachusetts"
    "MI"  "Michigan"
    "MN"  "Minnesota"
    "MS"  "Mississippi"
    "MO"  "Missouri"
    "MT"  "Montana"
    "NE"  "Nebraska"
    "NV"  "Nevada"
    "NH"  "New Hampshire"
    "NJ"  "New Jersey"
    "NM"  "New Mexico"
    "NY"  "New York"
    "NC"  "North Carolina"
    "ND"  "North Dakota"
    "OH"  "Ohio"
    "OK"  "Oklahoma"
    "OR"  "Oregon"
    "PA"  "Pennsylvania"
    "RI"  "Rhode Island"
    "SC"  "South Carolina"
    "SD"  "South Dakota"
    "TN"  "Tennessee"
    "TX"  "Texas"
    "UT"  "Utah"
    "VT"  "Vermont"
    "VA"  "Virginia"
    "WA"  "Washington"
    "WV"  "West Virginia"
    "WI"  "Wisconsin"
    "WY"  "Wyoming"
})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _STATESSEQ (seq _STATES))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListStates "List all the abbreviated states."

  []

  (keys _STATES))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindState "Return the full state name."

  ^String
  [^String code]

  (_STATES (cstr/upper-case code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindStateCode "Return the abbreviated state code."

  ^String
  [^String state]

  (when-let [rs (filter #(= (nth % 1) state) _STATESSEQ) ]
    (nth (first rs) 0)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private countries-eof nil)
