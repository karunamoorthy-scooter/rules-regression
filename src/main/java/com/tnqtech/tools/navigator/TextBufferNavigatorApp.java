package com.tnqtech.tools.navigator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Desktop UI that allows navigating the plain text output produced by the DOCX
 * extractor.
 */
public final class TextBufferNavigatorApp {

    private static final String FRAME_TITLE = "Text Buffer Navigator";
    private static final String NO_FILE_LOADED = "No file loaded";
    private static final String SELECTION_PROMPT = "Select text to view its position";
    private static final String LOAD_BUTTON_LABEL = "Load Text File";
    private static final String LOAD_DIALOG_TITLE = "Select extractor output file";
    private static final String TEXT_FILES_DESCRIPTION = "Text Files";
    private static final String TEXT_FILE_EXTENSION = "txt";
    private static final String POSITION_LABEL_TEXT = "Buffer Position:";
    private static final String HIGHLIGHT_BUTTON_LABEL = "Highlight";
    private static final String LOAD_FILE_FIRST_MESSAGE = "Please load a text file first.";
    private static final String ENTER_POSITION_MESSAGE = "Enter a buffer position to highlight.";
    private static final String INVALID_NUMBER_MESSAGE = "Invalid number. Please provide a valid buffer position.";
    private static final String ERROR_DIALOG_TITLE = "Error";
    private static final String INFO_DIALOG_TITLE = "Information";
    private static final String UNABLE_TO_LOAD_FILE_MESSAGE = "Unable to load file";
    private static final String NO_TEXT_FOUND_TEMPLATE = "No text found at position %d.";
    private static final String POSITION_TEMPLATE = "Position %d to %d: '%s'";
    private static final String SELECTION_TEMPLATE = "Selection %d to %d: '%s'";
    private static final String CARET_TEMPLATE = "Caret at position %d";

    private final TextBufferNavigator navigator = new TextBufferNavigator();
    private final JFrame frame = new JFrame(FRAME_TITLE);
    private final JTextArea textArea = new JTextArea();
    private final JLabel fileLabel = new JLabel(NO_FILE_LOADED, SwingConstants.LEFT);
    private final JLabel selectionLabel = new JLabel(SELECTION_PROMPT, SwingConstants.LEFT);
    private final JTextField positionField = new JTextField();

    private TextBufferNavigatorApp() {
        configureLookAndFeel();
        initializeUi();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final ClassNotFoundException | InstantiationException
                | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            // keep default look and feel if something goes wrong
        }
    }

    private void initializeUi() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(16, 16));
        frame.setMinimumSize(new Dimension(900, 600));

        final JPanel topPanel = createTopPanel();
        final JScrollPane scrollPane = createTextArea();
        final JPanel bottomPanel = createBottomPanel();

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private JPanel createTopPanel() {
        final JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        final JButton loadButton = new JButton(LOAD_BUTTON_LABEL);
        loadButton.addActionListener(this::handleLoadFile);

        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD));
        fileLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        panel.add(loadButton, BorderLayout.WEST);
        panel.add(fileLabel, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane createTextArea() {
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setMargin(new java.awt.Insets(12, 12, 12, 12));
        textArea.setBackground(new Color(250, 250, 250));
        textArea.addCaretListener(new SelectionListener());
        return new JScrollPane(textArea);
    }

    private JPanel createBottomPanel() {
        final JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

        selectionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        final JPanel navigationPanel = new JPanel(new BorderLayout(8, 8));
        final JLabel positionLabel = new JLabel(POSITION_LABEL_TEXT);
        positionField.setColumns(10);
        final JButton goButton = new JButton(HIGHLIGHT_BUTTON_LABEL);
        goButton.addActionListener(this::handleHighlightPosition);

        navigationPanel.add(positionLabel, BorderLayout.WEST);
        navigationPanel.add(positionField, BorderLayout.CENTER);
        navigationPanel.add(goButton, BorderLayout.EAST);

        panel.add(selectionLabel, BorderLayout.NORTH);
        panel.add(navigationPanel, BorderLayout.CENTER);
        return panel;
    }

    private void handleLoadFile(final ActionEvent event) {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(LOAD_DIALOG_TITLE);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(TEXT_FILES_DESCRIPTION, TEXT_FILE_EXTENSION));

        final int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            final Path selectedFile = chooser.getSelectedFile().toPath();
            try {
                navigator.load(selectedFile);
                textArea.setText(navigator.getBuffer());
                textArea.setCaretPosition(0);
                fileLabel.setText(selectedFile.toAbsolutePath().toString());
                selectionLabel.setText(SELECTION_PROMPT);
            } catch (final IOException ex) {
                showError(UNABLE_TO_LOAD_FILE_MESSAGE, ex);
            }
        }
    }

    private void handleHighlightPosition(final ActionEvent event) {
        if (!navigator.isLoaded()) {
            showMessage(LOAD_FILE_FIRST_MESSAGE);
            return;
        }
        final String input = positionField.getText().trim();
        if (input.isEmpty()) {
            showMessage(ENTER_POSITION_MESSAGE);
            return;
        }
        try {
            final int position = Integer.parseInt(input);
            final Optional<TextBufferNavigator.TextSelection> optionalSelection = navigator.extractTokenAt(position);
            if (optionalSelection.isEmpty()) {
                showMessage(String.format(Locale.ROOT, NO_TEXT_FOUND_TEMPLATE, position));
                return;
            }
            final TextBufferNavigator.TextSelection selection = optionalSelection.get();
            textArea.requestFocusInWindow();
            textArea.select(selection.start(), selection.end());
            selectionLabel.setText(String.format(Locale.ROOT, POSITION_TEMPLATE, selection.start(), selection.end(), selection.text()));
        } catch (final NumberFormatException ex) {
            showMessage(INVALID_NUMBER_MESSAGE);
        }
    }

    private void showError(final String message, final Exception ex) {
        JOptionPane.showMessageDialog(frame,
                message + "\n" + ex.getMessage(),
                ERROR_DIALOG_TITLE,
                JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(final String message) {
        JOptionPane.showMessageDialog(frame, message, INFO_DIALOG_TITLE, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showSelectionInfo(final int start, final int end) {
        if (!navigator.isLoaded()) {
            selectionLabel.setText(SELECTION_PROMPT);
            return;
        }
        if (start == end) {
            selectionLabel.setText(String.format(Locale.ROOT, CARET_TEMPLATE, start));
        } else {
            final String selectedText = navigator.getBuffer().substring(start, end);
            selectionLabel.setText(String.format(Locale.ROOT, SELECTION_TEMPLATE, start, end, selectedText));
        }
    }

    private final class SelectionListener implements CaretListener {
        @Override
        public void caretUpdate(final CaretEvent event) {
            final int start = Math.min(event.getDot(), event.getMark());
            final int end = Math.max(event.getDot(), event.getMark());
            showSelectionInfo(start, end);
            if (start != end) {
                positionField.setText(String.valueOf(start));
            }
        }
    }

    private void display() {
        frame.setVisible(true);
    }

    /**
     * Application entry point.
     *
     * @param args ignored
     */
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> new TextBufferNavigatorApp().display());
    }
}
