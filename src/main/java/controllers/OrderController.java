package controllers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import model.*;
import utils.Log;

public class OrderController {

  private static DatabaseController dbCon;

  public OrderController() {
    dbCon = new DatabaseController();
  }

  public static Order getOrder(int id) {

    // check for connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    // Build SQL string to query
    String sql =
            "SELECT *\n" +
                    "FROM orders\n" +
                    "  INNER JOIN user on user.id = orders.user_id\n" +
                    "  INNER JOIN address as billing_address ON orders.billing_address_id = billing_address.id\n" +
                    "  INNER JOIN address as shipping_address ON orders.shipping_address_id = shipping_address.id\n" +
                    "  INNER JOIN line_item ON orders.id = line_item.order_id\n" +
                    "  INNER JOIN product ON line_item.product_id = product.id\n" +
                    "WHERE order_id = " + id;


    try {
      ResultSet rs = dbCon.query(sql);
      if (rs.next()){
        return orderFactory(id, rs);
      }

    } catch (SQLException err){
      err.printStackTrace();
    }

    // Returns null
    return null;
  }


  private static Order orderFactory(int orderID, ResultSet rs) throws SQLException{

      User user = new User();
      user.setId(rs.getInt("user.id"));
      user.setFirstname(rs.getString("user.first_name"));
      user.setLastname(rs.getString("user.last_name"));
      user.setPassword(rs.getString("user.password"));
      user.setEmail(rs.getString("user.email"));
      user.setSalt(rs.getString("user.salt"));

      Address billingAddress = new Address();
      billingAddress.setId(rs.getInt("billing_address.id"));
      billingAddress.setName(rs.getString("billing_address.name"));
      billingAddress.setStreetAddress(rs.getString("billing_address.street_address"));
      billingAddress.setCity(rs.getString("billing_address.city"));
      billingAddress.setZipCode(rs.getString("billing_address.zipcode"));

      Address shippingAddress = new Address();
      shippingAddress.setId(rs.getInt("shipping_address.id"));
      shippingAddress.setName(rs.getString("shipping_address.name"));
      shippingAddress.setStreetAddress(rs.getString("shipping_address.street_address"));
      shippingAddress.setCity(rs.getString("shipping_address.city"));
      shippingAddress.setZipCode(rs.getString("shipping_address.zipcode"));

      //Test purposes ______
//      User user = UserController.getUser(rs.getInt("user_id"));
//      ArrayList<LineItem> lineItems = LineItemController.getLineItemsForOrder(rs.getInt("id"));
//      Address billingAddress = AddressController.getAddress(rs.getInt("billing_address_id"));
//      Address shippingAddress = AddressController.getAddress(rs.getInt("shipping_address_id"));

      ArrayList<LineItem> lineItems = new ArrayList<>();

      do {

        if (orderID == rs.getInt("orders.id")){
          lineItems.add(new LineItem(rs.getInt("line_item.id"),
                  new Product(
                          rs.getInt("product.id"),
                          rs.getString("product.product_name"),
                          rs.getString("product.sku"),
                          rs.getFloat("product.price"),
                          rs.getString("product.description"),
                          rs.getInt("product.stock"),
                          rs.getInt("product.created_at")),
                  rs.getInt("line_item.quantity"),
                  rs.getFloat("line_item.price")
          ));
        } else {
          break;
        }

      } while (rs.next());


      //Go back to the pointer before
      rs.previous();
      // Create an object instance of order from the database data
       Order order =
              new Order(
                      rs.getInt("orders.id"),
                      user,
                      lineItems,
                      billingAddress,
                      shippingAddress,
                      rs.getFloat("orders.order_total"),
                      rs.getLong("orders.created_at"),
                      rs.getLong("orders.updated_at"));

      // Returns the build order
      return order;

  }


  /**
   * Get all orders in database
   *
   * @return
   */
  public static ArrayList<Order> getOrders() {

    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    String sql =
            "SELECT *\n" +
                    "FROM orders\n" +
                    "  INNER JOIN user on user.id = orders.user_id\n" +
                    "  INNER JOIN address as billing_address ON orders.billing_address_id = billing_address.id\n" +
                    "  INNER JOIN address as shipping_address ON orders.shipping_address_id = shipping_address.id\n" +
                    "  INNER JOIN line_item ON orders.id = line_item.order_id\n" +
                    "  INNER JOIN product ON line_item.product_id = product.id";

    ArrayList<Order> orders = new ArrayList<Order>();

    try {
      ResultSet rs = dbCon.query(sql);
      while(rs.next()) {

        // Add order to our list
        orders.add(orderFactory(rs.getInt("orders.id"),rs));
      }
    } catch (SQLException ex) {
      System.out.println(ex.getMessage());
    }

    // return the orders
    return orders;
  }

  public static Order createOrder(Order order) {
    // If anything is missing return null
    if (order.getCustomer() == null
            || order.getBillingAddress() == null
            || order.getShippingAddress() == null){
      System.out.println("Returning");
      return null;
    }

    // Write in log that we've reach this step
    Log.writeLog(OrderController.class.getName(), order, "Actually creating an order in DB", 0);

    // Set creation and updated time for order.
    order.setCreatedAt(System.currentTimeMillis() / 1000L);
    order.setUpdatedAt(System.currentTimeMillis() / 1000L);

    // Check for DB Connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    // Save addresses to database and save them back to initial order instance
    order.setBillingAddress(AddressController.createAddress(order.getBillingAddress()));
    order.setShippingAddress(AddressController.createAddress(order.getShippingAddress()));

    // Save the user to the database and save them back to initial order instance
    if (UserController.getID(order.getCustomer().getEmail()) != 0){
      order.setCustomer(UserController.getUser(UserController.getID(order.getCustomer().getEmail())));
    } else {
      order.setCustomer(UserController.createUser(order.getCustomer()));
    }

    //Store full information about the item && Calculate each lineItem's price from SKU and QUANTITY
    for (LineItem lineitem:order.getLineItems()) {
      //Get information from the DB and store it in the lineItem
      Product lineProduct = ProductController.getProductBySku(lineitem.getProduct().getSku());
      lineitem.setProduct(lineProduct);
      lineitem.setPrice((float)lineitem.getQuantity() * lineProduct.getPrice());
    }


    try {
      // Insert the product in the DB
      int orderID = dbCon.insert(
              "INSERT INTO orders(user_id, billing_address_id, shipping_address_id, order_total, created_at, updated_at) VALUES("
                      + order.getCustomer().getId()
                      + ", "
                      + order.getBillingAddress().getId()
                      + ", "
                      + order.getShippingAddress().getId()
                      + ", "
                      + order.calculateOrderTotal()
                      + ", "
                      + order.getCreatedAt()
                      + ", "
                      + order.getUpdatedAt()
                      + ")");

      if (orderID != 0) {
        //Update the productid of the product before returning
        order.setId(orderID);
      }

      // Create an empty list in order to go trough items and then save them back with ID
      ArrayList<LineItem> items = new ArrayList<>();

      // Save line items to database
      for(LineItem item : order.getLineItems()){
        item = LineItemController.createLineItem(item, order.getId());
        items.add(item);
      }

      order.setLineItems(items);
    } catch (SQLException err){
      err.printStackTrace();
    }

    // Return order
    return order;
  }
}