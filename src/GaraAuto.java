import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GaraAuto {

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
    }

    /**
     *Classe Auto
     *
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
     *Classe Risultato
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
                        codaArrivi.put(new Risultato(p, System.currentTimeMillis()));
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

        public GestoreGara(Pista pista, List<Partecipante> lista) {
            this.pista = pista;
            this.partecipanti.addAll(lista);
            this.pool = Executors.newFixedThreadPool(lista.size());
        }

        public void avviaGaraEAttendiArrivi() {
            for (Partecipante p : partecipanti) {
                pool.submit(new PartecipanteRunnable(p, pista, segnalePartenza, codaArrivi, this));
            }

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

            System.out.println("\n--- CLASSIFICA ---");
            for (int i = 0; i < risultati.size(); i++) {
                Risultato r = risultati.get(i);
                System.out.printf("%d) %s - tempo(ms)=%d%n",
                        i + 1,
                        r.getPartecipante().getNome(),
                        r.getTempoArrivoMillis());
            }
        }

        public boolean isGaraFermata() { return garaFermata; }
    }
}
