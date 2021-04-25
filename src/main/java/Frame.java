import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

public class Frame extends JFrame {
    private DefaultTableModel model;
    private LocalDate today;
    private double prevBid = 0.0, prevAsk = 0.0;    //variables that help cover problem of comparing values between different queries
    private boolean firstV; //first value isn't compared to previous value, this flag helps bypass it in loop in getData()
    /**
     * Initialization of main frame
     */
    public Frame(){
        super("CUI");
        int height = Toolkit.getDefaultToolkit().getScreenSize().height;
        int width = Toolkit.getDefaultToolkit().getScreenSize().width;
        this.setSize(width/2,height/2);
        this.setLocation(width/4, height/4);
        this.setResizable(true);
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        initComponents();
    }

    /**
     * Initialization of frame components
     */
    private void initComponents(){
        today = LocalDate.now();
        JTextArea textArea = new JTextArea(1,10);
        textArea.setText(today.minusDays(30).toString());   //Initial date value
        JButton button = new JButton("Pokaż!");
        button.addActionListener(e->{
            try {
                createTable(textArea.getText());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });
        JPanel panel = new JPanel();
        panel.add(textArea);
        panel.add(button);
        this.getContentPane().add(panel,BorderLayout.NORTH);

        String[] columnNames = { "Data", "Cena zakupu","Róźnica zakupu", "Cena sprzedaży", "Różnica sprzedaży"};
        JTable table = new JTable();
        model = new DefaultTableModel();
        model.setColumnIdentifiers(columnNames);
        table.setModel(model);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setEnabled(true);
        this.getContentPane().add(scrollPane, BorderLayout.CENTER);
        try {   //Filling table with initial value
            createTable(textArea.getText());
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * function that checks correctness of parameter, gets data and displays it as a table
     * @param date first day of the range in which the data is displayed
     */
    private void createTable(String date) throws IOException {
        Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
        //verification of input data correctness
        if(!DATE_PATTERN.matcher(date).matches()){
            JOptionPane.showMessageDialog(this, "Data musi być w formacie \"YYYY-MM-DD\"", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        //checking if date fits in range of correct data
        LocalDate first = LocalDate.parse(date);
        if(first.isBefore(LocalDate.parse("2002-01-02"))){
            JOptionPane.showMessageDialog(this, "NBP przechowuje dane od 2 stycznia 2002 roku. \n " +
                    "Podana data nie obejmuje tego okresu.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if(first.isAfter(today.minusDays(3))){
            JOptionPane.showMessageDialog(this, "Proszę podać wcześniejszą datę", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        //Initialization of temporary values that help compare values between different queries
        model.setRowCount(0);
        prevBid = 0.0;
        prevAsk = 0.0;
        firstV = true;
        //NBP's api limitation is 93 days of data in one query, if range entered by user is bigger - we have to divide it
        LocalDate second = first.plusDays(92);
        while (second.isBefore(today)) {
            for (String[] array : getData(first.toString(), second.toString())) model.addRow(array);
            first = second.plusDays(1);
            second = second.plusDays(93);
        }
        for (String[] array : getData(first.toString(), today.toString())) model.addRow(array);
    }

    /**
     * function that gets data from NBP api and converts it from JSON to arraylist
     * @param startDate first date of range to get data from
     * @param endDate last date of range
     * @return ArrayList of table rows
     */
    private ArrayList<String[]> getData(String startDate, String endDate) throws IOException {
        URL url = new URL("http://api.nbp.pl/api/exchangerates/rates/C/USD/"+startDate+"/"+endDate+"/");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("accept", "application/json");
        InputStream responseStream = con.getInputStream();
        JsonReader reader = Json.createReader(responseStream);
        JsonObject object = reader.readObject();
        JsonArray rates = object.getJsonArray("rates");
        ArrayList<String[]> data = new ArrayList<>();
        // every value from rates array is passed to arraylist as string array which is later used as table row
        for(JsonObject rate : rates.getValuesAs(JsonObject.class)){
            String[] tmp = new String[5];
            tmp[0] = rate.getString("effectiveDate");
            tmp[1] = String.valueOf(rate.getJsonNumber("bid"));
            tmp[2] = String.valueOf(new DecimalFormat("#.######", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(rate.getJsonNumber("bid").doubleValue()-prevBid));
            tmp[3] = String.valueOf(rate.getJsonNumber("ask"));
            tmp[4] = String.valueOf(new DecimalFormat("#.######", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(rate.getJsonNumber("ask").doubleValue()-prevAsk));
            data.add(tmp);
            if(firstV){
                tmp[2]="-";
                tmp[4]="-";
                firstV=false;
            }
            prevAsk = rate.getJsonNumber("ask").doubleValue();
            prevBid = rate.getJsonNumber("bid").doubleValue();

        }
        return data;
    }
}
