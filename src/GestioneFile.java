import java.io.FileWriter;

public class GestioneFile {
    String fileName;

    public GestioneFile(String fileName){
        this.fileName=fileName;
    }

    // Metodo per leggere e stampare a video l'ultima classifica salvata
    synchronized void readLastRanking() {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("Nessuna classifica precedente trovata.");
            return;
        }
        System.out.println("\nUltima classifica salvata:");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            System.out.println("Fine della classifica precedente.\n");
        } catch (IOException e) {
            System.err.println("Errore durante la lettura della classifica: " + e.getMessage());
        }
    }

    // Metodo per salvare la classifica corrente
    private synchronized void salvaClassifica() {
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("Classifica dei passaggi:\n");
            for (Player player : players) {
                writer.write("Giocatore " + player.getName() + ": " + player.getPassesMade() + " passaggi\n");
            }
            System.out.println("Classifica salvata nel file 'classifica.txt'.");
        } catch (IOException e) {
            System.err.println("Errore durante il salvataggio della classifica: " + e.getMessage());
        }
    }
}

}
