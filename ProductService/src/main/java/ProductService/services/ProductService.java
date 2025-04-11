package ProductService.services;

import ProductService.configs.RedisService;
import ProductService.models.Products;
import ProductService.repository.ProductsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService {

    @Autowired
    private ProductsRepository productsRepository;

    @Autowired
    private RedisService redisService;

    public Products createProduct(Products product){
        return productsRepository.save(product);
    }
    public Products updateProduct(Products product){
        if (product.getId() == 0){
            throw new RuntimeException("Product id missing in params!");
        }
        Products existingProduct = productsRepository.findById(product.getId()).orElse(null);
        if (existingProduct == null){
            throw new RuntimeException("Product not found!");
        }
        existingProduct.setName(product.getName());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setActive(product.getActive());
        Products updatedProduct = productsRepository.save(existingProduct);
        deleteProductCache(product.getId());
        return updatedProduct;
    }

    public Products getProductById(int productId){
        String cacheKey = getProductIdCacheKey(productId);
        Products product = redisService.get(cacheKey, Products.class);
        if (product == null){
            product = productsRepository.findById(productId).orElse(null);
            if (product == null){
                throw new RuntimeException("Product not found!");
            }
            redisService.save(cacheKey, product, 30, TimeUnit.MINUTES);
        }
        return product;
    }

    public List<Products> getAllProducts(){
        return productsRepository.findAll();
    }

    public String getProductIdCacheKey(int productId){
        return "productId:" + productId;
    }

    public void deleteProductCache(int productId){
        String cacheKey = getProductIdCacheKey(productId);
        redisService.delete(cacheKey);
    }
}
