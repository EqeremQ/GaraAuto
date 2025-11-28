import java.util.*;

public class MainGara {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("Gara di Auto");

        System.out.print("Numero di auto partecipanti: ");
        int n;
        try { n = Integer.parseInt(sc.nextLine()); }
        catch (Exception e) { n = 3; }

        List<GaraAuto.Partecipante> partecipanti = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            System.out.print("Nome auto " + i + ": ");
            String nome = sc.nextLine().trim();
            if (nome.isEmpty()) nome = "Auto" + i;

            System.out.print("Velocità (m/s): ");
            double velocita;
            try { velocita = Double.parseDouble(sc.nextLine()); }
            catch (Exception e) { velocita = 5.0; }

            partecipanti.add(new GaraAuto.Auto("A" + i, nome, velocita));
        }

        System.out.print("Lunghezza pista (metri): ");
        double lunghezza;
        try { lunghezza = Double.parseDouble(sc.nextLine()); }
        catch (Exception e) { lunghezza = 100; }

        GaraAuto.Pista pista = new GaraAuto.Pista("P1", "Pista Principale", lunghezza);

        GaraAuto.GestoreGara gestore = new GaraAuto.GestoreGara(pista, partecipanti);

        System.out.println("La gara inizierà tra 2 secondi...");
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        gestore.avviaGaraEAttendiArrivi();

        System.out.println("\nGara terminata.");
    }
}
