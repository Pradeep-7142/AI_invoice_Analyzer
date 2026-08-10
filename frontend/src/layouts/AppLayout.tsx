import { useState } from "react";
import {
  AppBar,
  Avatar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import DashboardIcon from "@mui/icons-material/DashboardOutlined";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLongOutlined";
import StorefrontIcon from "@mui/icons-material/StorefrontOutlined";
import PaymentsOutlinedIcon from "@mui/icons-material/PaymentsOutlined";
import AccountBalanceWalletOutlinedIcon from "@mui/icons-material/AccountBalanceWalletOutlined";
import InsightsIcon from "@mui/icons-material/InsightsOutlined";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import GroupOutlinedIcon from "@mui/icons-material/GroupOutlined";
import TuneOutlinedIcon from "@mui/icons-material/TuneOutlined";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/AuthContext";

const DRAWER_WIDTH = 240;

const NAV_ITEMS = [
  { label: "Dashboard", to: "/dashboard", icon: <DashboardIcon /> },
  { label: "AI Copilot", to: "/ai-assistant", icon: <AutoAwesomeIcon color="primary" /> },
  { label: "Invoices", to: "/invoices", icon: <ReceiptLongIcon /> },
  { label: "Vendors", to: "/vendors", icon: <StorefrontIcon /> },
  { label: "Payments", to: "/payments", icon: <PaymentsOutlinedIcon /> },
  { label: "Budgets", to: "/budgets", icon: <AccountBalanceWalletOutlinedIcon /> },
  { label: "Analytics", to: "/analytics", icon: <InsightsIcon /> },
  { label: "Organization Users", to: "/settings/users", icon: <GroupOutlinedIcon /> },
  { label: "Finance Settings", to: "/settings/finance", icon: <TuneOutlinedIcon /> },
];

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<HTMLElement | null>(null);
  const { user, organization, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    setUserMenuAnchor(null);
    await logout();
    navigate("/login", { replace: true });
  };

  const drawerContent = (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <Toolbar>
        <Typography variant="h6" fontWeight={700} color="primary">
          InvoiceIQ
        </Typography>
      </Toolbar>
      <Divider />
      <List sx={{ flexGrow: 1 }}>
        {NAV_ITEMS.map((item) => (
          <ListItemButton
            key={item.to}
            component={NavLink}
            to={item.to}
            sx={{
              "&.active": {
                backgroundColor: "action.selected",
                borderRight: "3px solid",
                borderColor: "primary.main",
              },
            }}
          >
            <ListItemIcon>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { sm: `${DRAWER_WIDTH}px` },
          borderBottom: "1px solid",
          borderColor: "divider",
          backgroundColor: "background.paper",
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setMobileOpen(!mobileOpen)}
            sx={{ mr: 2, display: { sm: "none" } }}
          >
            <MenuIcon />
          </IconButton>
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="subtitle1">{organization?.name}</Typography>
          </Box>
          <IconButton onClick={(e) => setUserMenuAnchor(e.currentTarget)}>
            <Avatar sx={{ width: 32, height: 32, fontSize: 14 }}>
              {user?.fullName?.charAt(0).toUpperCase()}
            </Avatar>
          </IconButton>
          <Menu anchorEl={userMenuAnchor} open={!!userMenuAnchor} onClose={() => setUserMenuAnchor(null)}>
            <MenuItem disabled>{user?.email}</MenuItem>
            <Divider />
            <MenuItem onClick={handleLogout}>Sign out</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Box
        component="nav"
        sx={{ width: { sm: DRAWER_WIDTH }, flexShrink: { sm: 0 } }}
      >
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: "block", sm: "none" },
            "& .MuiDrawer-paper": { width: DRAWER_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: "none", sm: "block" },
            "& .MuiDrawer-paper": { width: DRAWER_WIDTH, boxSizing: "border-box" },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
