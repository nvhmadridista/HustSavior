package io.github.HustSavior.entities;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import io.github.HustSavior.collision.TileCollision;
import io.github.HustSavior.map.HighgroundManager;
import io.github.HustSavior.map.LowgroundManager;
import io.github.HustSavior.utils.GameConfig;

public abstract class AbstractMonster {
    protected static final short COLLISION_LAYER_BITS = 0x0001;
    protected static final short MONSTER_CATEGORY = 0x0002;
    protected static final short TRANSPARENCY_BOUNDS_BITS = 0x0004;
    protected static final short MONSTER_MASK = COLLISION_LAYER_BITS | TRANSPARENCY_BOUNDS_BITS;
    
    public enum MonsterState {
        IDLE, RUNNING, ATTACKING, TAKE_HIT, DEATH
    }
    
    // Base stats
    protected float hp;
    protected float attack;
    protected float speed;
    protected MonsterState currentState = MonsterState.IDLE;
    protected boolean isFlipped = false;
    
    // Physics
    protected Vector2 position;
    protected Vector2 velocity;
    protected Rectangle bounds;
    protected float width = 32f;  // Default width in pixels
    protected float height = 32f; // Default height in pixels
    
    // Animation components
    protected Animation<TextureRegion> idleAnimation;
    protected Animation<TextureRegion> runAnimation;
    protected Animation<TextureRegion> attack1Animation;
    protected Animation<TextureRegion> attack2Animation;
    protected Animation<TextureRegion> attack3Animation;
    protected Animation<TextureRegion> takeHitAnimation;
    protected Animation<TextureRegion> deathAnimation;
    protected float stateTime = 0f;
    
    protected boolean isCollidingWithPlayer = false;
    protected float attackCooldown = 0;
    protected static final float ATTACK_COOLDOWN_TIME = 2.0f; // 1 second between attacks
    
    // Add these new class variables at the top of the class
    protected static final float CONTACT_DAMAGE_COOLDOWN = 0.5f; // Time between contact damage
    protected static final float SWARM_RADIUS = 2.0f; // Radius for swarm behavior
    protected static final float PERSONAL_SPACE = 0.3f; // Minimum distance between monsters
    protected static final float DAMAGE_RADIUS = 0.5f; // Radius for damage dealing
    protected float contactDamageTimer = 0;
    
    // Add these constants at the top of the class
    protected static final float ATTACK_ANIMATION_DURATION = 2f; // Slower attack animation
    protected static final float DEFAULT_ATTACK_COOLDOWN = 2f;
    protected float ATTACK_COOLDOWN = DEFAULT_ATTACK_COOLDOWN; // Time between attacks
    protected static final float STATE_TRANSITION_DELAY = 0.6f; // Delay between state changes
    protected float stateTransitionTimer = 0.5f;
    protected MonsterState nextState = null;
    protected float attackTimer = ATTACK_COOLDOWN; // Initialize with cooldown
    
    protected boolean isFinishingAttack = false;
    
    // Add these constants at the top
    protected static final float PUSH_FORCE = 2.0f;
    protected static final float PUSH_RECOVERY_TIME = 0.2f;
    protected float pushRecoveryTimer = 0;
    
    protected TiledMap map;
    
    // Add visibility field
    private boolean visible = true;
    private String currentBoundsLayer = null;
    
    private boolean isTransparent = false;
    
    // Add getter/setter for visibility
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setCurrentBoundsLayer(String layer) {
        this.currentBoundsLayer = layer;
    }
    
    public String getCurrentBoundsLayer() {
        return currentBoundsLayer;
    }
    
    public void setTransparent(boolean transparent) {
        this.isTransparent = transparent;
    }
    
    public boolean isTransparent() {
        return isTransparent;
    }
    
    protected float DETECTION_RANGE = 200f;  
    protected float ATTACK_RANGE = 1f;     
    protected float CHASE_SPEED = 300f;     
    
    // Abstract methods that must be implemented by specific monsters
    public abstract void initializeAnimations();
    protected abstract void disposeMonster();
    
    // Common methods
    public void draw(SpriteBatch batch) {
        if (!visible || batch == null || getCurrentAnimation() == null) return;
        
        TextureRegion currentFrame = getCurrentAnimation().getKeyFrame(stateTime, true);
        if (currentFrame == null) return;
        
        float x = position.x - currentFrame.getRegionWidth() / 2;
        float y = position.y - currentFrame.getRegionHeight() / 2;
        
        if (isFlipped != currentFrame.isFlipX()) {
            currentFrame.flip(true, false);
        }
        
        batch.draw(currentFrame, x, y);
    }
    
    protected Animation<TextureRegion> getCurrentAnimation() {
        switch (currentState) {
            case IDLE: return idleAnimation;
            case RUNNING: return runAnimation;
            case ATTACKING: return attack1Animation; // Default to attack1
            case TAKE_HIT: return takeHitAnimation;
            case DEATH: return deathAnimation;
            default: return idleAnimation;
        }
    }
    
    protected void createBody(float x, float y) {
        position = new Vector2(x, y);
        velocity = new Vector2();
        
        bounds = new Rectangle(
            x/GameConfig.PPM - width/(2*GameConfig.PPM), 
            y/GameConfig.PPM - height/(2*GameConfig.PPM), 
            width/GameConfig.PPM,    // Make sure width is not 0
            height/GameConfig.PPM    // Make sure height is not 0
        );
        
        // Debug the initial bounds
        Gdx.app.debug("Monster", String.format(
            "Created bounds: x=%.2f, y=%.2f, w=%.2f, h=%.2f",
            bounds.x, bounds.y, bounds.width, bounds.height
        ));
    }
    
    public boolean isAlive() {
        return hp > 0;
    }
    
    public void takeDamage(float damage) {
        if (!isAlive()) return;
    
        // Apply damage
        hp -= damage;
        Gdx.app.log("Monster", "Taking damage: " + damage + ", HP remaining: " + hp);
    
        // Handle state changes
        if (hp <= 0) {
            hp = 0;
            handleDeath();
        } else if (currentState != MonsterState.DEATH) {
            // Enter hit state and apply knockback
            changeState(MonsterState.TAKE_HIT);
            stateTime = 0;
    
            // Apply knockback away from player
            Vector2 knockbackDir = new Vector2(
                position.x - player.getPosition().x,
                position.y - player.getPosition().y
            ).nor().scl(PUSH_FORCE * 2);  // Increased knockback force
            handlePush(knockbackDir);
        }
    }
    
    // Getters
    public Rectangle getBounds() {
        if (bounds == null) {
            Gdx.app.error("Monster", "Bounds is null in getBounds!");
            return new Rectangle(0, 0, width/GameConfig.PPM, height/GameConfig.PPM);
        }
        return new Rectangle(
            position.x/GameConfig.PPM - bounds.width/2,
            position.y/GameConfig.PPM - bounds.height/2,
            bounds.width,
            bounds.height
        );
    }
    public float getHp() { return hp; }
    public float getAttack() { return attack; }
    
    public void render(SpriteBatch batch) {
        if (!isAlive()) return;
        renderMonster(batch);  // Abstract method for specific monster rendering
    }
    
  
    protected  void renderMonster(SpriteBatch batch){
        if (isTransparent) {
            batch.setColor(1, 1, 1, 0.5f); // Example: 50% transparency
        }
        
        TextureRegion currentFrame = null;
        
        switch (currentState) {
            case IDLE:
                currentFrame = idleAnimation.getKeyFrame(stateTime, true);
                break;
            case RUNNING:
                currentFrame = runAnimation.getKeyFrame(stateTime, true);
                break;
            case ATTACKING:
                currentFrame = attack1Animation.getKeyFrame(stateTime, false);
                break;
            case TAKE_HIT:
                currentFrame = takeHitAnimation.getKeyFrame(stateTime, false);
                break;
            case DEATH:
                currentFrame = deathAnimation.getKeyFrame(stateTime, false);
                break;
        }
        
        if (currentFrame != null) {
            float x = position.x - currentFrame.getRegionWidth() / 2f;
            float y = position.y - currentFrame.getRegionHeight() / 2f;
            
            if (isFlipped) {
                batch.draw(currentFrame, 
                    x + currentFrame.getRegionWidth(), y,
                    -currentFrame.getRegionWidth(), currentFrame.getRegionHeight());
            } else {
                batch.draw(currentFrame, x, y);
            }
        }
        
        batch.setColor(1, 1, 1, 1); // Reset color
    }
    
    public void dispose() {
        disposeMonster();  // Abstract method for specific monster cleanup
    }
    
    
    public void init() {
        initializeAnimations();
    }
    
    // Add these fields
    protected HighgroundManager highgroundManager;
    protected LowgroundManager lowgroundManager;
    protected static final float POSITION_UPDATE_INTERVAL = 1/60f; // 60 times per second
    protected float positionUpdateTimer = 0;
    
    protected TileCollision tileCollision;
    
    public void setTileCollision(TileCollision tileCollision) {
        this.tileCollision = tileCollision;
    }
    
    // Add AI constants
    protected static final float AI_UPDATE_INTERVAL = 0.25f;
    protected float aiUpdateTimer = 0;
    protected boolean isAggro = false;

    protected static final float PPM = GameConfig.PPM;
    
    protected Player player;
    
    public AbstractMonster(Player player) {
        this.player = player;
    }
    
    public void update(float delta, Player player) {
        if (!isAlive() || player == null) return;

        // Update velocity (now includes collision checks)
        updateVelocity(player, delta);

        // Apply movement
        position.x += velocity.x * delta;
        position.y += velocity.y * delta;
        updateBounds();

        updateAnimation(delta);
        updateTimers(delta);
    }

    private void updateBounds() {
        if (bounds == null) {
            Gdx.app.error("Monster", "Bounds is null!");
            return;
        }

        // Update bounds position in world units
        bounds.setPosition(
            position.x/GameConfig.PPM - bounds.width/2,
            position.y/GameConfig.PPM - bounds.height/2
        );
    }

    private void updateVelocity(Player player, float delta) {
        Vector2 playerPos = player.getPosition();
        Vector2 toPlayer = new Vector2(
            playerPos.x - position.x,
            playerPos.y - position.y
        );
        float distanceToPlayer = toPlayer.len();

        if (distanceToPlayer <= DETECTION_RANGE) {
            isAggro = true;
            Vector2 direction = toPlayer.nor();
            isFlipped = direction.x < 0;

            if (distanceToPlayer <= ATTACK_RANGE && attackTimer <= 0) {
                velocity.setZero();
                changeState(MonsterState.ATTACKING);
                attackTimer = ATTACK_COOLDOWN;
                player.takeDamage(attack);
            } else if (currentState != MonsterState.ATTACKING) {
                changeState(MonsterState.RUNNING);
                
                // Calculate potential new position
                Vector2 desiredVelocity = direction.scl(CHASE_SPEED);
                Rectangle testBounds = new Rectangle(
                    (position.x + desiredVelocity.x * delta)/GameConfig.PPM - bounds.width/2,
                    (position.y + desiredVelocity.y * delta)/GameConfig.PPM - bounds.height/2,
                    bounds.width,
                    bounds.height
                );
                
                // If collision detected, stop. Otherwise, move towards player
                if (tileCollision != null && tileCollision.collidesWith(testBounds)) {
                    velocity.setZero();
                } else {
                    velocity.set(desiredVelocity);
                }
            }
        } else {
            isAggro = false;
            velocity.setZero();
            changeState(MonsterState.IDLE);
        }
    }

    protected void updateTimers(float delta) {
        if (attackTimer > 0) {
            attackTimer -= delta;
        }
        
        if (pushRecoveryTimer > 0) {
            pushRecoveryTimer -= delta;
        }
    }

   
    protected void updateAnimation(float delta) {
        stateTime += delta;
    
        if (currentState == MonsterState.ATTACKING) {
            if (stateTime >= attack1Animation.getAnimationDuration()) {
                isFinishingAttack = false;
                changeState(MonsterState.RUNNING);
                stateTime = 0;
                attackTimer = ATTACK_COOLDOWN;
            }
        }
    
        if (currentState == MonsterState.TAKE_HIT && takeHitAnimation.isAnimationFinished(stateTime)) {
            changeState(MonsterState.IDLE);
        }
    
        if (currentState == MonsterState.DEATH && deathAnimation.isAnimationFinished(stateTime)) {
            // Remove monster from screen and return to pool
            removeFromScreen();
        }
    }
    
    //Add setter for managers
    public void setGroundManagers(HighgroundManager highgroundManager, LowgroundManager lowgroundManager) {
        this.highgroundManager = highgroundManager;
        this.lowgroundManager = lowgroundManager;
    }
    
    // In constructor or after setting initial stats:
   
    public Vector2 getPosition() {
        return position;
    }
    
    public void setPosition(float x, float y) {
        position.set(x, y);
        bounds.setPosition(x, y);
    }
    
    public void setVelocity(float x, float y) {
        velocity.set(x, y);
    }
    


    protected void changeState(MonsterState newState) {
        if (currentState != newState) {
            Gdx.app.log("Monster", String.format(
                "State Change: %s -> %s", 
                currentState, 
                newState
            ));
            currentState = newState;
            stateTime = 0;
        }
    }

    public void handlePush(Vector2 pushVelocity) {
        if (pushRecoveryTimer <= 0) {
            velocity.add(pushVelocity);
            pushRecoveryTimer = PUSH_RECOVERY_TIME;
        }
    }

    // Add this getter method
    public MonsterState getCurrentState() {
        return currentState;
    }

    // Add this method to set monster size
    protected void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        // Update bounds if they exist
        if (bounds != null) {
            bounds.setSize(width/GameConfig.PPM, height/GameConfig.PPM);
        }
    }

    // Add this method to render debug bounds
    public void renderDebug(ShapeRenderer shapeRenderer) {
        if (bounds == null) return;
        
        // Save previous color
        Color prevColor = shapeRenderer.getColor().cpy();
        
        // Draw monster bounds
        shapeRenderer.setColor(Color.GREEN); // Green for monster bounds
        Rectangle worldBounds = getBounds();
        shapeRenderer.rect(
            worldBounds.x * GameConfig.PPM,
            worldBounds.y * GameConfig.PPM,
            worldBounds.width * GameConfig.PPM,
            worldBounds.height * GameConfig.PPM
        );
        
    
        
        // Restore previous color
        shapeRenderer.setColor(prevColor);
    }

    protected void handleDeath() {
        changeState(MonsterState.DEATH);
        stateTime = 0;
    }

    protected void removeFromScreen() {
        // Implement logic to remove monster from screen and return to pool
        // This could involve setting a flag or calling a method in the MonsterPool class
        // For example:
        MonsterPool.getInstance().free(this);
    }

}