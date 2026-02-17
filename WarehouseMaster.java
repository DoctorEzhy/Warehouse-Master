import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

abstract class AbstractProduct implements Serializable {
    private final int id;
    private final String name;
    private final double price;
    private final int quantity;

    public AbstractProduct(int id, String name, double price, int quantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }

    @Override
    public String toString() {
        return "ID=" + id +
                ", Name='" + name + '\'' +
                ", Price=" + price +
                ", Qty=" + quantity;
    }
}

class FoodProduct extends AbstractProduct {
    private final LocalDate expirationDate;

    public FoodProduct(int id, String name, double price, int quantity, LocalDate expirationDate) {
        super(id, name, price, quantity);
        this.expirationDate = expirationDate;
    }

    public LocalDate getExpirationDate() { return expirationDate; }

    @Override
    public String toString() {
        return "Food{" + super.toString() + ", Expiration=" + expirationDate + "}";
    }
}

class ElectronicsProduct extends AbstractProduct {
    private final int warrantyMonths;

    public ElectronicsProduct(int id, String name, double price, int quantity, int warrantyMonths) {
        super(id, name, price, quantity);
        this.warrantyMonths = warrantyMonths;
    }

    public int getWarrantyMonths() { return warrantyMonths; }

    @Override
    public String toString() {
        return "Electronics{" + super.toString() + ", WarrantyMonths=" + warrantyMonths + "}";
    }
}

class Warehouse {
    private static Warehouse instance;
    private final List<AbstractProduct> products = new ArrayList<>();
    private int nextId = 1;

    private Warehouse() {}

    public static synchronized Warehouse getInstance() {
        if (instance == null) instance = new Warehouse();
        return instance;
    }

    public synchronized int generateId() { return nextId++; }

    private synchronized void updateNextId(int id) {
        if (id >= nextId) nextId = id + 1;
    }

    public synchronized void addProduct(AbstractProduct p) {
        products.add(p);
        updateNextId(p.getId());
    }

    public synchronized void removeProductById(int id) { products.removeIf(p -> p.getId() == id); }

    public synchronized List<AbstractProduct> getAllProducts() { return new ArrayList<>(products); }

    public synchronized List<FoodProduct> getExpiredFoodProducts() {
        LocalDate now = LocalDate.now();
        return products.stream()
                .filter(p -> p instanceof FoodProduct)
                .map(p -> (FoodProduct) p)
                .filter(fp -> fp.getExpirationDate().isBefore(now))
                .collect(Collectors.toList());
    }

    public synchronized void saveToCSV(String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            for (AbstractProduct p : products) {
                if (p instanceof FoodProduct f) {
                    pw.printf(Locale.US, "%d,food,%s,%.2f,%d,%s%n",
                            f.getId(), f.getName(), f.getPrice(), f.getQuantity(), f.getExpirationDate());
                } else if (p instanceof ElectronicsProduct e) {
                    pw.printf(Locale.US, "%d,electronics,%s,%.2f,%d,%d%n",
                            e.getId(), e.getName(), e.getPrice(), e.getQuantity(), e.getWarrantyMonths());
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка сохранения CSV: " + e.getMessage());
        }
    }

    public synchronized void loadFromCSV(String filename) {
        products.clear();
        nextId = 1;

        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("Файл не найден, создаётся новый.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length != 6) {
                        System.out.println("Пропущена повреждённая строка: " + line);
                        continue;
                    }

                    int id = Integer.parseInt(parts[0]);
                    String type = parts[1].toLowerCase();
                    String name = parts[2];
                    double price = Double.parseDouble(parts[3].replace(",", "."));
                    int quantity = Integer.parseInt(parts[4]);

                    if ("food".equals(type)) {
                        LocalDate expDate = LocalDate.parse(parts[5]);
                        addProduct(new FoodProduct(id, name, price, quantity, expDate));
                    } else if ("electronics".equals(type)) {
                        int warranty = Integer.parseInt(parts[5]);
                        addProduct(new ElectronicsProduct(id, name, price, quantity, warranty));
                    } else {
                        System.out.println("Неизвестный тип: " + line);
                    }
                } catch (Exception ex) {
                    System.out.println("Ошибка в строке: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения CSV: " + e.getMessage());
        }
    }
}

public class WarehouseMaster {
    private static final Warehouse warehouse = Warehouse.getInstance();
    private static final Scanner scanner = new Scanner(System.in);
    private static final String FILE_PATH =
            System.getProperty("user.dir") + File.separator + "products.csv";
    private static volatile boolean exitRequested = false;

    public static void main(String[] args) {
        System.out.println("Рабочая директория: " + System.getProperty("user.dir"));
        warehouse.loadFromCSV(FILE_PATH);
        startExpirationChecker();
        mainMenu();
        exitRequested = true;
        warehouse.saveToCSV(FILE_PATH);
        System.out.println("Склад сохранён. До свидания!");
        scanner.close();
    }

    private static void startExpirationChecker() {
        Thread t = new Thread(() -> {
            while (!exitRequested) {
                try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}
                List<FoodProduct> expired = warehouse.getExpiredFoodProducts();
                if (!expired.isEmpty()) {
                    System.out.println("\n[ВНИМАНИЕ] Просроченные продукты:");
                    expired.forEach(System.out::println);
                    System.out.print("> ");
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void mainMenu() {
        while (true) {
            System.out.println("\n=== Главное меню ===");
            System.out.println("1. Показать все продукты");
            System.out.println("2. Показать просроченные");
            System.out.println("3. Добавить продукт");
            System.out.println("4. Удалить продукт");
            System.out.println("5. Сохранить");
            System.out.println("0. Выход");
            System.out.print("Выберите пункт: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> {
                    List<AbstractProduct> all = warehouse.getAllProducts();
                    if (all.isEmpty()) System.out.println("Склад пуст.");
                    else all.forEach(System.out::println);
                }
                case "2" -> {
                    List<FoodProduct> expired = warehouse.getExpiredFoodProducts();
                    if (expired.isEmpty()) System.out.println("Просроченных нет.");
                    else expired.forEach(System.out::println);
                }
                case "3" -> addProduct();
                case "4" -> {
                    System.out.print("ID для удаления: ");
                    int id = Integer.parseInt(scanner.nextLine());
                    warehouse.removeProductById(id);
                    System.out.println("Удалено.");
                }
                case "5" -> {
                    warehouse.saveToCSV(FILE_PATH);
                    System.out.println("Склад сохранён.");
                }
                case "0" -> { return; }
                default -> System.out.println("Неверный выбор.");
            }
        }
    }

    private static void addProduct() {
        try {
            System.out.print("Тип (food/electronics): ");
            String type = scanner.nextLine().trim().toLowerCase();
            int id = warehouse.generateId();

            System.out.print("Название: ");
            String name = scanner.nextLine();

            System.out.print("Цена: ");
            String priceInput = scanner.nextLine().replace(",", ".");
            double price = Double.parseDouble(priceInput);

            System.out.print("Количество: ");
            int qty = Integer.parseInt(scanner.nextLine());

            if ("food".equals(type)) {
                System.out.print("Дата окончания (yyyy-MM-dd): ");
                LocalDate expDate = LocalDate.parse(scanner.nextLine());
                warehouse.addProduct(new FoodProduct(id, name, price, qty, expDate));
            } else if ("electronics".equals(type)) {
                System.out.print("Гарантия (в месяцах): ");
                int warranty = Integer.parseInt(scanner.nextLine());
                warehouse.addProduct(new ElectronicsProduct(id, name, price, qty, warranty));
            } else {
                System.out.println("Неизвестный тип.");
                return;
            }
            System.out.println("Продукт добавлен с ID = " + id);
        } catch (Exception e) {
            System.out.println("Ошибка ввода.");
        }
    }
}
