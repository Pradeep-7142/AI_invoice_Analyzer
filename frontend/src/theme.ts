import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#1E3A8A",
    },
    secondary: {
      main: "#0EA5A4",
    },
    background: {
      default: "#F4F6F8",
      paper: "#FFFFFF",
    },
    error: {
      main: "#DC2626",
    },
    warning: {
      main: "#D97706",
    },
    success: {
      main: "#16A34A",
    },
  },
  shape: {
    borderRadius: 10,
  },
  typography: {
    fontFamily: [
      "Inter",
      "-apple-system",
      "BlinkMacSystemFont",
      "Segoe UI",
      "Roboto",
      "Helvetica Neue",
      "Arial",
      "sans-serif",
    ].join(","),
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: {
          boxShadow: "0 1px 2px rgba(15, 23, 42, 0.08)",
        },
      },
    },
    MuiAppBar: {
      defaultProps: {
        color: "inherit",
        elevation: 0,
      },
    },
  },
});
