import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";

const ThemeContext =
  createContext(null);

const THEME_KEY =
  "appTheme";

function getInitialTheme() {

  const savedTheme =
    localStorage.getItem(
      THEME_KEY
    );

  if (
    savedTheme === "dark" ||
    savedTheme === "light"
  ) {

    return savedTheme;
  }

  const prefersDark =
    window.matchMedia &&
    window.matchMedia(
      "(prefers-color-scheme: dark)"
    ).matches;

  return prefersDark
    ? "dark"
    : "light";
}

export function ThemeProvider({
  children
}) {

  const [
    theme,
    setTheme
  ] = useState(
    getInitialTheme
  );

  useEffect(
    () => {

      document.documentElement.setAttribute(
        "data-theme",
        theme
      );

      document.documentElement.style.colorScheme =
        theme;

      localStorage.setItem(
        THEME_KEY,
        theme
      );

    },
    [theme]
  );

  function toggleTheme() {

    setTheme(
      currentTheme =>
        currentTheme === "dark"
          ? "light"
          : "dark"
    );
  }

  const value =
    useMemo(
      () => ({
        theme,
        toggleTheme,
        isDark:
          theme === "dark"
      }),
      [theme]
    );

  return (
    <ThemeContext.Provider
      value={value}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {

  const context =
    useContext(
      ThemeContext
    );

  if (!context) {

    throw new Error(
      "useTheme must be used inside ThemeProvider"
    );
  }

  return context;
}