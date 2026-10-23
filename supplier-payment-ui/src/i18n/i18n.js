import i18n
  from "i18next";

import {
  initReactI18next
} from "react-i18next";

import en
  from "./locales/en.json";

import hi
  from "./locales/hi.json";

import fr
  from "./locales/fr.json";

import ru
  from "./locales/ru.json";

import ar
  from "./locales/ar.json";

const savedLanguage =
  localStorage.getItem(
    "language"
  ) ||
  "en";

i18n
  .use(
    initReactI18next
  )
  .init({
    resources: {
      en: {
        translation: en
      },

      hi: {
        translation: hi
      },

      fr: {
        translation: fr
      },

      ru: {
        translation: ru
      },

      ar: {
        translation: ar
      }
    },

    lng:
      savedLanguage,

    fallbackLng:
      "en",

    interpolation: {
      escapeValue:
        false
    }
  });

document.documentElement.lang =
  savedLanguage;

document.documentElement.dir =
  savedLanguage === "ar"
    ? "rtl"
    : "ltr";

export default i18n;