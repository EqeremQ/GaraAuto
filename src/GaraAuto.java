import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GaraAuto - versione con podio sincronizzato
 */
public class GaraAuto {

    /**
     * Classe astratta Partecipante
     */
    public static abstract class Partecipante {
        protected final String id;
        protected final String nome;
        protected volatile double posizione = 0.0;   // metri percorsi
        protected volatile double velocita;          // metri al secondo
        protected final AtomicBoolean ritirato = new AtomicBoolean(false);

        public Partecipante(String id, String nome, double velocita) {
            this.id = id;
            this.nome = nome;
            this.velocita = velocita;
        }

        public void avanzaUnSecondo() {
            posizione += velocita;
        }

        public double getPosizione() { return posizione; }
        public String getId() { return id; }
        public String getNome() { return nome; }
        public boolean isRitirato() { return ritirato.get(); }
        public void ritira() { ritirato.set(true); }
    }

    /**
     * Classe Auto
     */
    public static class Auto extends Partecipante {
        public Auto(String id, String nome, double velocita) {
            super(id, nome, velocita);
        }
    }

    /**
     * Classe Pista
     */
    public static class Pista {
        private final String id;
        private final String nome;
        private final double lunghezza;

        public Pista(String id, String nome, double lunghezza) {
            this.id = id;
            this.nome = nome;
            this.lunghezza = lunghezza;
        }

        public double getLunghezza() { return lunghezza; }
        public String getNome() { return nome; }
    }

    /**
     * Classe Risultato
     */
    public static class Risultato {
        private final Partecipante partecipante;
        private final long tempoArrivoMillis;

        public Risultato(Partecipante p, long t) {
            this.partecipante = p;
            this.tempoArrivoMillis = t;
        }

        public Partecipante getPartecipante() { return partecipante; }
        public long getTempoArrivoMillis() { return tempoArrivoMillis; }
    }

    /**
     * Thread di un partecipante
     */
    public static class PartecipanteRunnable implements Runnable {
        private final Partecipante p;
        private final Pista pista;
        private final CountDownLatch segnalePartenza;
        private final BlockingQueue<Risultato> codaArrivi;
        private final GestoreGara gestore;

        public PartecipanteRunnable(Partecipante p, Pista pista, CountDownLatch segnalePartenza,
                                    BlockingQueue<Risultato> codaArrivi, GestoreGara gestore) {
            this.p = p;
            this.pista = pista;
            this.segnalePartenza = segnalePartenza;
            this.codaArrivi = codaArrivi;
            this.gestore = gestore;
        }

        @Override
        public void run() {
            try {
                segnalePartenza.await();

                while (p.getPosizione() < pista.getLunghezza() && !gestore.isGaraFermata()) {

                    long inizio = System.currentTimeMillis();

                    p.avanzaUnSecondo();

                    if (p.getPosizione() >= pista.getLunghezza()) {
                        // usa il metodo registrazione centrale per aggiornare il podio in modo sincronizzato
                        Risultato r = new Risultato(p, System.currentTimeMillis());
                        try {
                            gestore.registraArrivo(r);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        break;
                    }

                    long passato = System.currentTimeMillis() - inizio;
                    long attesa = 1000 - passato;
                    if (attesa > 0) Thread.sleep(attesa);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Gestore della gara
     */
    public static class GestoreGara {
        private final List<Partecipante> partecipanti = new CopyOnWriteArrayList<>();
        private final Pista pista;
        private final CountDownLatch segnalePartenza = new CountDownLatch(1);
        private final BlockingQueue<Risultato> codaArrivi = new LinkedBlockingQueue<>();
        private final ExecutorService pool;
        private volatile boolean garaFermata = false;

        // Podio e relativo lock (v3.0)
        private final List<Risultato> podio = new ArrayList<>();
        private final Object lockPodio = new Object();

        public GestoreGara(Pista pista, List<Partecipante> lista) {
            this.pista = pista;
            this.partecipanti.addAll(lista);
            this.pool = Executors.newFixedThreadPool(Math.max(1, lista.size()));
        }

        /**
         * Avvia i runnable dei partecipanti (senza dare il via).
         */
        public void avviaPartecipanti() {
            for (Partecipante p : partecipanti) {
                pool.submit(new PartecipanteRunnable(p, pista, segnalePartenza, codaArrivi, this));
            }
        }

        /**
         * Metodo da usare dai runnable per registrare un arrivo in modo sincronizzato.
         */
        public void registraArrivo(Risultato r) throws InterruptedException {
            synchronized (lockPodio) {
                podio.add(r);
                System.out.println("Arrivo registrato (podio): "
                        + r.getPartecipante().getNome()
                        + " posizione: " + podio.size());
            }
            // notifica anche la coda (Giudice legge da qui)
            codaArrivi.put(r);
        }

        /**
         * Restituisce una copia del podio (snapshot) protetta dal lock.
         */
        public List<Risultato> getPodioSnapshot() {
            synchronized (lockPodio) {
                return new ArrayList<>(podio);
            }
        }

        public void avviaGaraEAttendiArrivi() {
            // metodo di comodo: sottomette e dà il via (compatibile con v1.0)
            avviaPartecipanti();

            try { Thread.sleep(200); } catch (InterruptedException e) {}

            System.out.println("Via!");
            segnalePartenza.countDown();

            List<Risultato> risultati = new ArrayList<>();

            try {
                for (int i = 0; i < partecipanti.size(); i++) {
                    Risultato r = codaArrivi.take();
                    risultati.add(r);
                    System.out.println((i + 1) + "° posto: " + r.getPartecipante().getNome());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            pool.shutdownNow();

            System.out.println("\nClassifica");
            for (int i = 0; i < risultati.size(); i++) {
                Risultato r = risultati.get(i);
                System.out.printf("%d) %s - tempo(ms)=%d%n",
                        i + 1,
                        r.getPartecipante().getNome(),
                        r.getTempoArrivoMillis());
            }
        }

        public boolean isGaraFermata() { return garaFermata; }

        // Getter e utilità per il Giudice (v2.0 compatibilità)
        public CountDownLatch getSegnalePartenza() { return segnalePartenza; }
        public BlockingQueue<Risultato> getCodaArrivi() { return codaArrivi; }
        public int getNumeroPartecipanti() { return partecipanti.size(); }
        public void chiudiPool() { pool.shutdownNow(); }
    }

    /**
     * Giudice effettua il conto alla rovescia, dà il via e monitora gli arrivi.
     */
    public static class Giudice implements Runnable {
        private final GestoreGara gestore;

        public Giudice(GestoreGara gestore) {
            this.gestore = gestore;
        }

        @Override
        public void run() {
            try {
                // Conto alla rovescia prima della partenza
                System.out.println("Giudice: partenza fra 3...");
                Thread.sleep(1000);
                System.out.println("3");
                Thread.sleep(1000);
                System.out.println("2");
                Thread.sleep(1000);
                System.out.println("1");
                Thread.sleep(300);
                System.out.println("Giudice: via");
                // dà il via
                gestore.getSegnalePartenza().countDown();

                // Raccoglie gli arrivi dalla coda e annuncia ogni taglio del traguardo
                List<Risultato> classificaParziale = new ArrayList<>();
                int tot = gestore.getNumeroPartecipanti();
                for (int i = 0; i < tot; i++) {
                    Risultato r = gestore.getCodaArrivi().take(); // blocking
                    classificaParziale.add(r);
                    System.out.println("Giudice: " + r.getPartecipante().getNome() + " ha tagliato il traguardo! (" + (i+1) + "°)");
                }

                // Stampa classifica finale
                System.out.println("\nClassifica Finale");
                for (int i = 0; i < classificaParziale.size(); i++) {
                    Risultato r = classificaParziale.get(i);
                    System.out.printf("%d) %s - tempo(ms)=%d%n",
                            i + 1,
                            r.getPartecipante().getNome(),
                            r.getTempoArrivoMillis());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Giudice: interrotto.");
            } finally {
                // Assicura la chiusura del pool dei partecipanti
                gestore.chiudiPool();
                System.out.println("Giudice: Gara terminata.");
            }
        }
    }
}
