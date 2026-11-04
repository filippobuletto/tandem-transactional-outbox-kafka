package io.quarkiverse.tandem.it;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The business change of the scenarios, written through Hibernate ORM on the outbox's datasource. */
@Entity
@Table(name = "orders")
public class PlacedOrder {

    @Id
    public String id;

    public String status;

    protected PlacedOrder() {
    }

    public PlacedOrder(String id) {
        this.id = id;
        this.status = "PLACED";
    }
}
