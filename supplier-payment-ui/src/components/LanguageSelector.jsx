import {
  useTranslation
} from "react-i18next";

export default function LanguageSelector() {

  const {
    i18n
  } = useTranslation();

  async function changeLanguage(
    event
  ) {

    const language =
      event.target.value;

    await i18n.changeLanguage(
      language
    );

    localStorage.setItem(
      "language",
      language
    );

    document.documentElement.lang =
      language;

    document.documentElement.dir =
      language === "ar"
        ? "rtl"
        : "ltr";
  }

  const currentLanguage =
    i18n.resolvedLanguage ||
    i18n.language ||
    "en";

  return (
    <select
      value={
        currentLanguage
      }
      onChange={
        changeLanguage
      }
      aria-label="Language"
      className="language-selector"
    >
      <option value="en">
        English
      </option>

      <option value="hi">
        हिन्दी
      </option>

      <option value="fr">
        Français
      </option>

      <option value="ru">
        Русский
      </option>

      <option value="ar">
        العربية
      </option>
    </select>
  );
}