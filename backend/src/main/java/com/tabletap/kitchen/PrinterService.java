package com.tabletap.kitchen;

import com.tabletap.domain.*;
import com.tabletap.dto.PrinterDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.OrderRepository;
import com.tabletap.repository.PrinterRepository;
import com.tabletap.service.AccessService;
import com.tabletap.service.ApiException;
import com.tabletap.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Prints kitchen dockets. Observer pattern: it listens for OrderPlaced and prints after the order is
 * safely committed, on a background thread, so a jammed printer never slows down or blocks an order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterService {
    private static final int TIMEOUT_MS = 3000;

    private final PrinterRepository printers;
    private final OrderRepository orders;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- automatic printing

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPlaced(OrderPlaced event) {
        orders.findById(event.orderId()).ifPresent(this::printToAll);
    }

    // ---------------------------------------------------------------- owner / staff actions

    @Transactional(readOnly = true)
    public List<PrinterView> list(AppUser u, Long restaurantId) {
        access.requireWork(u, restaurants.load(restaurantId));
        return printers.findByRestaurantIdOrderByName(restaurantId).stream().map(PrinterView::of).toList();
    }

    @Transactional
    public PrinterView create(AppUser u, Long restaurantId, PrinterRequest req) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireManage(u, r);
        Printer p = new Printer();
        p.setRestaurant(r);
        apply(p, req);
        return PrinterView.of(printers.save(p));
    }

    @Transactional
    public PrinterView update(AppUser u, Long id, PrinterRequest req) {
        Printer p = load(u, id);
        apply(p, req);
        return PrinterView.of(p);
    }

    @Transactional
    public void delete(AppUser u, Long id) {
        printers.delete(load(u, id));
    }

    /** Prints a short test docket and reports whether the printer answered. */
    @Transactional
    public PrinterView test(AppUser u, Long id) {
        Printer p = load(u, id);
        Docket test = () -> List.of(Docket.Line.big("TABLETAP TEST"), Docket.Line.of("Printer: " + p.getName()),
            Docket.Line.of("If you can read this, printing works."));
        send(p, EscPos.encode(test, p.isUtf8()));
        return PrinterView.of(p);
    }

    /** Staff can reprint a docket from the kitchen screen (paper jam, docket lost…). */
    @Transactional
    public int reprint(AppUser u, Long orderId) {
        CustomerOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        access.requireWork(u, o.getRestaurant());
        return printToAll(o);
    }

    // ---------------------------------------------------------------- internals

    private int printToAll(CustomerOrder o) {
        ZoneId zone = Restaurants.zone(o.getRestaurant());
        int printed = 0;
        for (Printer p : printers.findByRestaurantIdAndActiveTrue(o.getRestaurant().getId())) {
            BasicDocket basic = new BasicDocket(o, p.getStation(), zone);
            if (basic.items().isEmpty()) continue; // nothing for this station
            if (send(p, EscPos.encode(Dockets.forStation(o, p.getStation(), zone), p.isUtf8()))) printed++;
        }
        return printed;
    }

    private boolean send(Printer p, byte[] bytes) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(checkedAddress(p.getHost()), p.getPort()), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(bytes);
            out.flush();
            p.setLastPrintedAt(Instant.now());
            p.setLastError(null);
            return true;
        } catch (Exception e) {
            log.warn("Printer '{}' at {}:{} failed: {}", p.getName(), p.getHost(), p.getPort(), e.getMessage());
            p.setLastError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            events.publishEvent(LiveEvent.of(LiveEvent.PRINTER_CHANGED, p.getRestaurant(), null));
            return false;
        }
    }

    /**
     * Printers live on the restaurant's own network. Only private (10.x, 172.16-31.x, 192.168.x) and
     * localhost addresses are allowed, so the printer setting can't be used to reach other machines
     * such as cloud metadata services.
     */
    static InetAddress checkedAddress(String host) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(host);
        if (!(addr.isSiteLocalAddress() || addr.isLoopbackAddress()))
            throw new UnknownHostException("Printers must be on the local network (e.g. 192.168.x.x)");
        return addr;
    }

    private Printer load(AppUser u, Long id) {
        Printer p = printers.findById(id).orElseThrow(() -> ApiException.notFound("Printer"));
        access.requireManage(u, p.getRestaurant());
        return p;
    }

    private void apply(Printer p, PrinterRequest req) {
        try {
            checkedAddress(req.host().trim());
        } catch (UnknownHostException e) {
            throw ApiException.badRequest(e.getMessage() != null && e.getMessage().startsWith("Printers")
                ? e.getMessage() : "Can't find a printer at " + req.host());
        }
        p.setName(req.name().trim());
        p.setHost(req.host().trim());
        p.setPort(req.port());
        p.setStation(Stations.normalise(req.station()));
        if (req.active() != null) p.setActive(req.active());
        if (req.utf8() != null) p.setUtf8(req.utf8());
    }
}
