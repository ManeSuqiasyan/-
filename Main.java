import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Swing ինտերֆեյս առաջադրանքների կառավարման համար։ */
public final class Main extends JFrame {
    private static final Color BACKGROUND = new Color(245, 247, 251);
    private static final Color INK = new Color(31, 41, 55);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color BLUE = new Color(37, 99, 235);

    private final TaskManager manager;
    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextField searchField = new JTextField(22);
    private final JComboBox<FilterOption<TaskStatus>> statusFilter = new JComboBox<>();
    private final JComboBox<FilterOption<Priority>> priorityFilter = new JComboBox<>();
    private final JLabel countLabel = new JLabel(" ");

    private Main(TaskManager manager) {
        super("Առաջադրանքների կառավարում");
        this.manager = manager;
        buildWindow();
        refresh();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                setSystemLookAndFeel();
                new Main(new TaskManager(Paths.get("tasks.csv"))).setVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Չհաջողվեց բեռնել տվյալների ֆայլը։\n" + e.getMessage(),
                        "Ֆայլի սխալ", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void setSystemLookAndFeel() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) { /* Կօգտագործվի Java-ի լռելյայն տեսքը։ */ }
    }

    private void buildWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 580));
        setSize(1120, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout(0, 16));

        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setBackground(BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(26, 30, 22, 30));
        page.add(createHeader(), BorderLayout.NORTH);
        page.add(createTablePanel(), BorderLayout.CENTER);
        page.add(createFooter(), BorderLayout.SOUTH);
        add(page, BorderLayout.CENTER);
        wireFilters();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 18));
        header.setOpaque(false);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Իմ առաջադրանքները");
        title.setFont(new Font("Dialog", Font.BOLD, 27));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Կազմակերպեք ձեր աշխատանքը մեկ տեղում");
        subtitle.setForeground(MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.CENTER);
        heading.add(titles, BorderLayout.WEST);

        JButton addButton = button("＋  Ավելացնել առաջադրանք", BLUE, Color.BLACK);
        addButton.addActionListener(e -> showTaskDialog(null));
        heading.add(addButton, BorderLayout.EAST);
        header.add(heading, BorderLayout.NORTH);

        JPanel tools = new JPanel(new GridBagLayout());
        tools.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 10);
        c.gridy = 0;
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        searchField.setToolTipText("Որոնել ըստ անվանման");
        searchField.setPreferredSize(new Dimension(260, 38));
        searchField.setFont(new Font("Dialog", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 233)),
                BorderFactory.createEmptyBorder(7, 11, 7, 11)));
        searchField.putClientProperty("JTextField.placeholderText", "Որոնել առաջադրանք...");
        tools.add(searchField, c);

        c.weightx = 0; c.fill = GridBagConstraints.NONE; c.gridx++;
        configureFilter(statusFilter, "Բոլոր կարգավիճակները", TaskStatus.values());
        tools.add(statusFilter, c);
        c.gridx++;
        configureFilter(priorityFilter, "Բոլոր առաջնահերթությունները", Priority.values());
        tools.add(priorityFilter, c);
        header.add(tools, BorderLayout.CENTER);
        return header;
    }

    private <T> void configureFilter(JComboBox<FilterOption<T>> combo, String allLabel, T[] values) {
        combo.setModel(new DefaultComboBoxModel<>());
        combo.addItem(new FilterOption<>(allLabel, null));
        for (T value : values) combo.addItem(new FilterOption<>(label(value), value));
        combo.setPreferredSize(new Dimension(205, 38));
        combo.setFont(new Font("Dialog", Font.PLAIN, 13));
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 233, 240)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        table.setRowHeight(48);
        table.setFont(new Font("Dialog", Font.PLAIN, 13));
        table.setForeground(INK);
        table.setGridColor(new Color(238, 241, 245));
        table.setShowVerticalLines(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 12));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(new Color(249, 250, 252));
        table.getTableHeader().setPreferredSize(new Dimension(0, 42));
        table.setDefaultRenderer(Object.class, new CellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(310);
        table.getColumnModel().getColumn(2).setPreferredWidth(115);
        table.getColumnModel().getColumn(3).setPreferredWidth(125);
        table.getColumnModel().getColumn(4).setPreferredWidth(145);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) showTaskDialog(selectedTask());
            }
        });
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        countLabel.setForeground(MUTED);
        footer.add(countLabel, BorderLayout.WEST);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        JButton statusButton = button("Փոխել կարգավիճակը", new Color(232, 239, 255), BLUE);
        statusButton.addActionListener(e -> changeStatus());
        JButton editButton = button("Փոփոխել", Color.WHITE, INK);
        editButton.addActionListener(e -> editSelected());
        JButton deleteButton = button("Ջնջել", new Color(254, 242, 242), new Color(185, 28, 28));
        deleteButton.addActionListener(e -> deleteSelected());
        JButton exportButton = button("Պահպանել ցանկը", new Color(236, 253, 245), new Color(4, 120, 87));
        exportButton.addActionListener(e -> exportTasks());
        actions.add(exportButton); actions.add(statusButton); actions.add(editButton); actions.add(deleteButton);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private JButton button(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(new Font("Dialog", Font.BOLD, 13));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.equals(Color.WHITE) ? new Color(220, 225, 233) : background),
                BorderFactory.createEmptyBorder(9, 13, 9, 13)));
        return button;
    }

    private void wireFilters() {
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        statusFilter.addActionListener(e -> refresh());
        priorityFilter.addActionListener(e -> refresh());
    }

    @SuppressWarnings("unchecked")
    private void refresh() {
        FilterOption<TaskStatus> statusOption = (FilterOption<TaskStatus>) statusFilter.getSelectedItem();
        FilterOption<Priority> priorityOption = (FilterOption<Priority>) priorityFilter.getSelectedItem();
        TaskStatus status = statusOption == null ? null : statusOption.value;
        Priority priority = priorityOption == null ? null : priorityOption.value;
        List<Task> filtered = new ArrayList<>(manager.filter(status, priority));
        String query = searchField.getText().trim();
        if (!query.isEmpty()) {
            List<Task> matches = manager.searchByTitle(query);
            filtered.removeIf(task -> !contains(matches, task.getId()));
        }
        tableModel.setTasks(filtered);
        countLabel.setText(filtered.size() + " առաջադրանք");
    }

    private boolean contains(List<Task> tasks, UUID id) {
        for (Task task : tasks) if (task.getId().equals(id)) return true;
        return false;
    }

    private Task selectedTask() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        return tableModel.getTask(table.convertRowIndexToModel(row));
    }

    private void editSelected() {
        Task task = selectedTask();
        if (task == null) { info("Նախ ընտրեք առաջադրանք։"); return; }
        showTaskDialog(task);
    }

    private void deleteSelected() {
        Task task = selectedTask();
        if (task == null) { info("Նախ ընտրեք առաջադրանք։"); return; }
        int answer = JOptionPane.showConfirmDialog(this,
                "Ջնջե՞լ «" + task.getTitle() + "» առաջադրանքը։", "Հաստատել ջնջումը",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try { manager.delete(task.getId()); refresh(); }
        catch (IOException | IllegalArgumentException e) { error(e.getMessage()); }
    }

    private void changeStatus() {
        Task task = selectedTask();
        if (task == null) { info("Նախ ընտրեք առաջադրանք։"); return; }
        JComboBox<TaskStatus> choices = new JComboBox<>(TaskStatus.values());
        choices.setSelectedItem(task.getStatus());
        choices.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getLabel());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            if (selected) { label.setBackground(list.getSelectionBackground()); label.setForeground(list.getSelectionForeground()); }
            return label;
        });
        int result = JOptionPane.showConfirmDialog(this, choices, "Ընտրեք կարգավիճակը",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try { manager.changeStatus(task.getId(), (TaskStatus) choices.getSelectedItem()); refresh(); }
        catch (IOException | IllegalArgumentException e) { error(e.getMessage()); }
    }

    private void exportTasks() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Պահպանել առաջադրանքների ցանկը");
        FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV ֆայլ (*.csv)", "csv");
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Տեքստային ֆայլ (*.txt)", "txt");
        chooser.addChoosableFileFilter(csvFilter);
        chooser.addChoosableFileFilter(txtFilter);
        chooser.setFileFilter(csvFilter);
        chooser.setSelectedFile(new java.io.File("tasks.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        boolean csv = chooser.getFileFilter() == csvFilter;
        String extension = csv ? ".csv" : ".txt";
        Path path = chooser.getSelectedFile().toPath();
        String filename = path.getFileName().toString();
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(extension)) {
            path = path.resolveSibling(filename + extension);
        }
        if (Files.exists(path)) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "Ֆայլն արդեն գոյություն ունի։ Փոխարինե՞լ այն։", "Հաստատել պահպանումը",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) return;
        }

        try {
            writeExport(path, tableModel.tasks, csv);
            JOptionPane.showMessageDialog(this,
                    "Ցուցադրվող " + tableModel.tasks.size() + " առաջադրանքը պահպանվեց։\n" + path.toAbsolutePath(),
                    "Պահպանումը հաջողվեց", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            error("Չհաջողվեց պահպանել ֆայլը։\n" + e.getMessage());
        }
    }

    private void writeExport(Path path, List<Task> tasks, boolean csv) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            if (csv) {
                writer.write("Անվանում,Նկարագրություն,Ստեղծման ամսաթիվ,Առաջնահերթություն,Կարգավիճակ");
                writer.newLine();
                for (Task task : tasks) {
                    writer.write(csv(task.getTitle())); writer.write(',');
                    writer.write(csv(task.getDescription())); writer.write(',');
                    writer.write(csv(task.getCreationDate().toString())); writer.write(',');
                    writer.write(csv(task.getPriority().getLabel())); writer.write(',');
                    writer.write(csv(task.getStatus().getLabel())); writer.newLine();
                }
            } else {
                for (Task task : tasks) {
                    writer.write("Անվանում: " + task.getTitle()); writer.newLine();
                    writer.write("Նկարագրություն: " + task.getDescription()); writer.newLine();
                    writer.write("Ստեղծման ամսաթիվ: " + task.getCreationDate()); writer.newLine();
                    writer.write("Առաջնահերթություն: " + task.getPriority().getLabel()); writer.newLine();
                    writer.write("Կարգավիճակ: " + task.getStatus().getLabel()); writer.newLine();
                    writer.write("----------------------------------------"); writer.newLine();
                }
            }
        }
    }

    private String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private void showTaskDialog(Task task) {
        boolean editing = task != null;
        JDialog dialog = new JDialog(this, editing ? "Փոփոխել առաջադրանքը" : "Նոր առաջադրանք", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JTextField titleField = new JTextField(editing ? task.getTitle() : "", 26);
        JTextArea descriptionArea = new JTextArea(editing ? task.getDescription() : "", 5, 26);
        descriptionArea.setLineWrap(true); descriptionArea.setWrapStyleWord(true);
        JTextField dateField = new JTextField(editing ? task.getCreationDate().toString() : LocalDate.now().toString(), 26);
        dateField.setEditable(!editing);
        dateField.setBackground(editing ? new Color(245, 247, 251) : Color.WHITE);
        JComboBox<Priority> priorityBox = new JComboBox<>(Priority.values());
        JComboBox<TaskStatus> statusBox = new JComboBox<>(TaskStatus.values());
        priorityBox.setSelectedItem(editing ? task.getPriority() : Priority.MEDIUM);
        statusBox.setSelectedItem(editing ? task.getStatus() : TaskStatus.OPEN);
        priorityBox.setRenderer(new EnumRenderer<Priority>());
        statusBox.setRenderer(new EnumRenderer<TaskStatus>());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(20, 22, 12, 22));
        addFormRow(form, 0, "Անվանում *", titleField);
        addFormRow(form, 1, "Նկարագրություն", new JScrollPane(descriptionArea));
        addFormRow(form, 2, "Ստեղծման ամսաթիվ *", dateField);
        addFormRow(form, 3, "Առաջնահերթություն *", priorityBox);
        addFormRow(form, 4, "Կարգավիճակ *", statusBox);

        JButton cancel = button("Չեղարկել", Color.WHITE, INK);
        JButton save = button(editing ? "Պահպանել" : "Ստեղծել առաջադրանք", BLUE, Color.WHITE);
        JPanel buttons = new JPanel(); buttons.setBackground(Color.WHITE);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 16, 18));
        buttons.add(cancel); buttons.add(save);
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            try {
                String titleValue = titleField.getText();
                String descriptionValue = descriptionArea.getText();
                Priority priority = (Priority) priorityBox.getSelectedItem();
                TaskStatus status = (TaskStatus) statusBox.getSelectedItem();
                if (editing) manager.update(task.getId(), titleValue, descriptionValue, priority, status);
                else manager.add(titleValue, descriptionValue, parseDate(dateField.getText()), priority, status);
                dialog.dispose(); refresh();
            } catch (DateTimeParseException ex) {
                error("Ամսաթիվը մուտքագրեք YYYY-MM-DD ձևաչափով, օրինակ՝ 2026-09-23։");
            } catch (IllegalArgumentException | IOException ex) {
                error(ex.getMessage());
            }
        });
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private LocalDate parseDate(String value) { return LocalDate.parse(value.trim()); }

    private void addFormRow(JPanel panel, int row, String caption, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(7, 0, 7, 16);
        JLabel label = new JLabel(caption); label.setForeground(INK); label.setFont(new Font("Dialog", Font.BOLD, 13));
        panel.add(label, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(7, 0, 7, 0);
        if (field instanceof JScrollPane) field.setPreferredSize(new Dimension(310, 100));
        else field.setPreferredSize(new Dimension(310, 36));
        panel.add(field, c);
    }

    private void info(String message) { JOptionPane.showMessageDialog(this, message, "Տեղեկություն", JOptionPane.INFORMATION_MESSAGE); }
    private void error(String message) { JOptionPane.showMessageDialog(this, message == null ? "Անհայտ սխալ։" : message, "Սխալ", JOptionPane.ERROR_MESSAGE); }

    private static String label(Object value) {
        if (value instanceof TaskStatus) return ((TaskStatus) value).getLabel();
        if (value instanceof Priority) return ((Priority) value).getLabel();
        return String.valueOf(value);
    }

    private static final class FilterOption<T> {
        private final String label;
        private final T value;
        private FilterOption(String label, T value) { this.label = label; this.value = value; }
        @Override public String toString() { return label; }
    }

    private static class EnumRenderer<T> extends JLabel implements javax.swing.ListCellRenderer<T> {
        @Override public Component getListCellRendererComponent(javax.swing.JList<? extends T> list, T value,
                int index, boolean selected, boolean focus) {
            setText(value == null ? "" : label(value));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            if (selected) { setBackground(list.getSelectionBackground()); setForeground(list.getSelectionForeground()); }
            else { setBackground(Color.WHITE); setForeground(INK); }
            return this;
        }
    }

    private static final class CellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            if (!selected) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 251, 253));
                setForeground(INK);
            }
            if (value instanceof TaskStatus || value instanceof Priority) setText(label(value));
            if (column == 1 && value != null) setForeground(MUTED);
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }

    private static final class TaskTableModel extends AbstractTableModel {
        private final String[] columns = {"Անվանում", "Նկարագրություն", "Ստեղծման ամսաթիվ", "Առաջնահերթություն", "Կարգավիճակ"};
        private List<Task> tasks = new ArrayList<>();
        void setTasks(List<Task> tasks) { this.tasks = new ArrayList<>(tasks); fireTableDataChanged(); }
        Task getTask(int row) { return tasks.get(row); }
        @Override public int getRowCount() { return tasks.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int row, int column) {
            Task task = tasks.get(row);
            switch (column) {
                case 0: return task.getTitle();
                case 1: return task.getDescription();
                case 2: return task.getCreationDate();
                case 3: return task.getPriority();
                case 4: return task.getStatus();
                default: return "";
            }
        }
    }
}
