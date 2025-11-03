package org.example.app.core.ui.wallet;

import org.example.app.core.ui.wallet.WalletController.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class WalletView extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(WalletView.class.getName());
    
    private JTabbedPane tabbedPane;
    private JList<String> walletList;
    private DefaultListModel<String> walletListModel;
    private JLabel currentWalletLabel, balanceLabel;
    private JButton createWalletBtn, loadWalletBtn, deleteWalletBtn;
    private JTextField toAddressField, amountField, gasPriceField;
    private JButton sendTransactionBtn;
    private JTextField tokenNameField, tokenSymbolField, tokenDecimalsField, tokenSupplyField;
    private JButton createTokenBtn;
    
    private final WalletController walletController;
    private String currentWalletId;
    
    private static final Color PRIMARY_COLOR = new Color(0, 255, 136);
    private static final Color BACKGROUND_COLOR = new Color(26, 26, 46);
    private static final Color SECONDARY_COLOR = new Color(22, 33, 62);
    private static final Color TEXT_COLOR = Color.WHITE;
    
    public WalletView() {
        this.walletController = new WalletController();
        initializeUI();
        setupEventHandlers();
        refreshWalletList();
    }
    
    private void initializeUI() {
        setTitle("TINC Blockchain - Wallet Management");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        add(createHeader(), BorderLayout.NORTH);
        
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(BACKGROUND_COLOR);
        tabbedPane.setForeground(TEXT_COLOR);
        
        createWalletManagementTab();
        createTransactionTab();
        createTokenTab();
        
        add(tabbedPane, BorderLayout.CENTER);
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BACKGROUND_COLOR);
        header.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("TINC Wallet Management");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(PRIMARY_COLOR);
        
        currentWalletLabel = new JLabel("No wallet selected");
        currentWalletLabel.setForeground(TEXT_COLOR);
        
        balanceLabel = new JLabel("Balance: 0 TINC");
        balanceLabel.setForeground(PRIMARY_COLOR);
        
        header.add(titleLabel, BorderLayout.WEST);
        
        JPanel rightPanel = new JPanel();
        rightPanel.setBackground(BACKGROUND_COLOR);
        rightPanel.add(currentWalletLabel);
        rightPanel.add(balanceLabel);
        header.add(rightPanel, BorderLayout.EAST);
        
        return header;
    }
    
    private void createWalletManagementTab() {
        JPanel walletPanel = new JPanel(new BorderLayout());
        walletPanel.setBackground(BACKGROUND_COLOR);
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(BACKGROUND_COLOR);
        leftPanel.setBorder(createTitledBorder("Wallets"));
        
        walletListModel = new DefaultListModel<>();
        walletList = new JList<>(walletListModel);
        walletList.setBackground(SECONDARY_COLOR);
        walletList.setForeground(TEXT_COLOR);
        walletList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onWalletSelected();
            }
        });
        
        JScrollPane walletScrollPane = new JScrollPane(walletList);
        walletScrollPane.setPreferredSize(new Dimension(300, 400));
        
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        buttonPanel.setBackground(BACKGROUND_COLOR);
        
        createWalletBtn = createStyledButton("Create Wallet");
        loadWalletBtn = createStyledButton("Load Wallet");
        deleteWalletBtn = createStyledButton("Delete Wallet");
        
        buttonPanel.add(createWalletBtn);
        buttonPanel.add(loadWalletBtn);
        buttonPanel.add(deleteWalletBtn);
        
        leftPanel.add(walletScrollPane, BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        walletPanel.add(leftPanel, BorderLayout.WEST);
        tabbedPane.addTab("Wallets", walletPanel);
    }
    
    private void createTransactionTab() {
        JPanel transactionPanel = new JPanel(new GridBagLayout());
        transactionPanel.setBackground(BACKGROUND_COLOR);
        transactionPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        transactionPanel.add(createLabel("To Address:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        toAddressField = createStyledTextField();
        transactionPanel.add(toAddressField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        transactionPanel.add(createLabel("Amount:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        amountField = createStyledTextField();
        transactionPanel.add(amountField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        transactionPanel.add(createLabel("Gas Price:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        gasPriceField = createStyledTextField();
        gasPriceField.setText("1000000000");
        transactionPanel.add(gasPriceField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        sendTransactionBtn = createStyledButton("Send Transaction");
        transactionPanel.add(sendTransactionBtn, gbc);
        
        tabbedPane.addTab("Transactions", transactionPanel);
    }
    
    private void createTokenTab() {
        JPanel tokenPanel = new JPanel(new GridBagLayout());
        tokenPanel.setBackground(BACKGROUND_COLOR);
        tokenPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        tokenPanel.add(createLabel("Token Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        tokenNameField = createStyledTextField();
        tokenPanel.add(tokenNameField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        tokenPanel.add(createLabel("Symbol:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        tokenSymbolField = createStyledTextField();
        tokenPanel.add(tokenSymbolField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        tokenPanel.add(createLabel("Decimals:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        tokenDecimalsField = createStyledTextField();
        tokenDecimalsField.setText("18");
        tokenPanel.add(tokenDecimalsField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        tokenPanel.add(createLabel("Supply:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        tokenSupplyField = createStyledTextField();
        tokenPanel.add(tokenSupplyField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        createTokenBtn = createStyledButton("Create Token");
        tokenPanel.add(createTokenBtn, gbc);
        
        tabbedPane.addTab("Tokens", tokenPanel);
    }
    
    private void setupEventHandlers() {
        createWalletBtn.addActionListener(e -> showCreateWalletDialog());
        loadWalletBtn.addActionListener(e -> showLoadWalletDialog());
        deleteWalletBtn.addActionListener(e -> deleteSelectedWallet());
        sendTransactionBtn.addActionListener(e -> sendTransaction());
        createTokenBtn.addActionListener(e -> createToken());
    }
    
    private void onWalletSelected() {
        String selectedWallet = walletList.getSelectedValue();
        if (selectedWallet != null) {
            currentWalletId = selectedWallet;
            updateWalletDisplay();
        }
    }
    
    private void showCreateWalletDialog() {
        JDialog dialog = new JDialog(this, "Create Wallet", true);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        panel.add(new JLabel("Wallet ID:"));
        JTextField walletIdField = new JTextField();
        panel.add(walletIdField);
        
        panel.add(new JLabel("Password:"));
        JPasswordField passwordField = new JPasswordField();
        panel.add(passwordField);
        
         JButton createBtn = new JButton("Create");
       JButton cancelBtn = new JButton("Cancel");
       
       createBtn.addActionListener(e -> {
           String walletId = walletIdField.getText().trim();
           String password = new String(passwordField.getPassword());
           
           if (walletId.isEmpty() || password.isEmpty()) {
               JOptionPane.showMessageDialog(dialog, "Please fill all fields");
               return;
           }
           
           WalletCreationResult result = walletController.createWallet(walletId, password, false);
           if (result.success) {
               JOptionPane.showMessageDialog(dialog, "Wallet created successfully!");
               refreshWalletList();
               dialog.dispose();
           } else {
               JOptionPane.showMessageDialog(dialog, result.message);
           }
       });
       
       cancelBtn.addActionListener(e -> dialog.dispose());
       
       panel.add(createBtn);
       panel.add(cancelBtn);
       
       dialog.add(panel);
       dialog.setVisible(true);
   }
   
   private void showLoadWalletDialog() {
       String selectedWallet = walletList.getSelectedValue();
       if (selectedWallet == null) {
           JOptionPane.showMessageDialog(this, "Please select a wallet");
           return;
       }
       
       String password = JOptionPane.showInputDialog(this, "Enter password:");
       if (password != null && !password.trim().isEmpty()) {
           WalletLoadResult result = walletController.loadWallet(selectedWallet, password);
           if (result.success) {
               currentWalletId = selectedWallet;
               updateWalletDisplay();
               JOptionPane.showMessageDialog(this, "Wallet loaded successfully!");
           } else {
               JOptionPane.showMessageDialog(this, result.message);
           }
       }
   }
   
   private void deleteSelectedWallet() {
       String selectedWallet = walletList.getSelectedValue();
       if (selectedWallet == null) {
           JOptionPane.showMessageDialog(this, "Please select a wallet");
           return;
       }
       
       int confirm = JOptionPane.showConfirmDialog(this, 
           "Delete wallet: " + selectedWallet + "?", 
           "Confirm Delete", JOptionPane.YES_NO_OPTION);
           
       if (confirm == JOptionPane.YES_OPTION) {
           if (walletController.deleteWallet(selectedWallet)) {
               refreshWalletList();
               JOptionPane.showMessageDialog(this, "Wallet deleted successfully!");
           } else {
               JOptionPane.showMessageDialog(this, "Failed to delete wallet");
           }
       }
   }
   
   private void sendTransaction() {
       if (currentWalletId == null) {
           JOptionPane.showMessageDialog(this, "Please load a wallet first");
           return;
       }
       
       try {
           String toAddress = toAddressField.getText().trim();
           long amount = Long.parseLong(amountField.getText().trim());
           long gasPrice = Long.parseLong(gasPriceField.getText().trim());
           
           TransactionResult result = walletController.createTransaction(currentWalletId, toAddress, amount, gasPrice, 21000);
           if (result.success) {
               JOptionPane.showMessageDialog(this, "Transaction sent successfully!");
               updateWalletDisplay();
               toAddressField.setText("");
               amountField.setText("");
           } else {
               JOptionPane.showMessageDialog(this, result.message);
           }
       } catch (NumberFormatException e) {
           JOptionPane.showMessageDialog(this, "Please enter valid numbers");
       }
   }
   
   private void createToken() {
       if (currentWalletId == null) {
           JOptionPane.showMessageDialog(this, "Please load a wallet first");
           return;
       }
       
       try {
           String name = tokenNameField.getText().trim();
           String symbol = tokenSymbolField.getText().trim();
           int decimals = Integer.parseInt(tokenDecimalsField.getText().trim());
           long supply = Long.parseLong(tokenSupplyField.getText().trim());
           
           TokenCreationResult result = walletController.createToken(currentWalletId, name, symbol, decimals, supply, false);
           if (result.success) {
               JOptionPane.showMessageDialog(this, "Token created successfully!");
               tokenNameField.setText("");
               tokenSymbolField.setText("");
               tokenSupplyField.setText("");
           } else {
               JOptionPane.showMessageDialog(this, result.message);
           }
       } catch (NumberFormatException e) {
           JOptionPane.showMessageDialog(this, "Please enter valid numbers");
       }
   }
   
   private JLabel createLabel(String text) {
       JLabel label = new JLabel(text);
       label.setForeground(TEXT_COLOR);
       return label;
   }
   
   private JTextField createStyledTextField() {
       JTextField field = new JTextField(20);
       field.setBackground(SECONDARY_COLOR);
       field.setForeground(TEXT_COLOR);
       return field;
   }
   
   private JButton createStyledButton(String text) {
       JButton button = new JButton(text);
       button.setBackground(SECONDARY_COLOR);
       button.setForeground(TEXT_COLOR);
       button.setFocusPainted(false);
       return button;
   }
   
   private TitledBorder createTitledBorder(String title) {
       TitledBorder border = BorderFactory.createTitledBorder(title);
       border.setTitleColor(PRIMARY_COLOR);
       return border;
   }
   
   private void refreshWalletList() {
       walletListModel.clear();
       List<WalletInfo> wallets = walletController.listWallets();
       for (WalletInfo wallet : wallets) {
           walletListModel.addElement(wallet.walletId);
       }
   }
   
   private void updateWalletDisplay() {
       if (currentWalletId != null) {
           WalletBalanceInfo balanceInfo = walletController.getWalletBalance(currentWalletId);
           if (balanceInfo != null) {
               currentWalletLabel.setText("Wallet: " + currentWalletId);
               balanceLabel.setText("Balance: " + balanceInfo.nativeBalance + " TINC");
           }
       }
   }
   
   public static void main(String[] args) {
       SwingUtilities.invokeLater(() -> {
           try {
               new WalletView().setVisible(true);
           } catch (Exception e) {
               e.printStackTrace();
           }
       });
   }
}