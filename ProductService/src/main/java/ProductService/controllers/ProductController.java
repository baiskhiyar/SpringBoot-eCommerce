package ProductService.controllers;

import ProductService.models.Products;
import ProductService.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("spring/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("healthCheck")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("data", "Product Service is up and running");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    @PostMapping("createProduct")
    private ResponseEntity<?> createProduct(@RequestBody Products product){
        try{
            return new ResponseEntity<>(productService.createProduct(product), HttpStatus.CREATED);
        } catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("updateProduct")
    private ResponseEntity<?> updateProduct(@RequestBody Products product){
        try{
            return new ResponseEntity<>(productService.updateProduct(product), HttpStatus.OK);
        } catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("listProducts")
    private ResponseEntity<?> listAllProducts(){
        try{
            return new ResponseEntity<>(productService.getAllProducts(), HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("listProducts/{productId}")
    private ResponseEntity<?> getById(@PathVariable Integer productId){
        try{
            return new ResponseEntity<>(productService.getProductById(productId), HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

}
