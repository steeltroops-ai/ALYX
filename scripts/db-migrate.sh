#!/bin/bash

# ALYX Database Migration Script
# This script handles database migrations for different environments

set -e

echo "🗄️  ALYX Database Migration Tool"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}[MIGRATE]${NC} $1"
}

# Configuration
ENVIRONMENT="${1:-local}"
ACTION="${2:-migrate}"

# Database connection parameters
case "$ENVIRONMENT" in
    "local")
        DB_HOST="localhost"
        DB_PORT="5432"
        DB_NAME="alyx"
        DB_USER="alyx_user"
        DB_PASSWORD="alyx_password"
        ;;
    "staging")
        DB_HOST="${STAGING_DB_HOST:-staging-postgres.alyx.svc.cluster.local}"
        DB_PORT="${STAGING_DB_PORT:-5432}"
        DB_NAME="${STAGING_DB_NAME:-alyx}"
        DB_USER="${STAGING_DB_USER:-alyx_user}"
        DB_PASSWORD="${STAGING_DB_PASSWORD}"
        ;;
    "production")
        DB_HOST="${PROD_DB_HOST}"
        DB_PORT="${PROD_DB_PORT:-5432}"
        DB_NAME="${PROD_DB_NAME:-alyx}"
        DB_USER="${PROD_DB_USER}"
        DB_PASSWORD="${PROD_DB_PASSWORD}"
        ;;
    *)
        print_error "Invalid environment: $ENVIRONMENT. Use 'local', 'staging', or 'production'"
        exit 1
        ;;
esac

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed. Please install Maven first."
        exit 1
    fi
    
    if ! command -v psql &> /dev/null; then
        print_error "PostgreSQL client is not installed. Please install postgresql-client first."
        exit 1
    fi
    
    # Check if required environment variables are set for non-local environments
    if [ "$ENVIRONMENT" != "local" ]; then
        if [ -z "$DB_PASSWORD" ]; then
            print_error "Database password not set. Please set ${ENVIRONMENT^^}_DB_PASSWORD environment variable."
            exit 1
        fi
    fi
    
    print_status "Prerequisites check passed ✓"
}

# Test database connection
test_connection() {
    print_status "Testing database connection..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT version();" > /dev/null 2>&1; then
        print_status "Database connection successful ✓"
    else
        print_error "Cannot connect to database. Please check connection parameters."
        exit 1
    fi
}

# Create backup before migration
create_backup() {
    if [ "$ENVIRONMENT" = "production" ] || [ "$ACTION" = "migrate" ]; then
        print_header "Creating database backup..."
        
        backup_dir="backups/$(date +%Y%m%d_%H%M%S)"
        mkdir -p "$backup_dir"
        
        export PGPASSWORD="$DB_PASSWORD"
        
        # Create schema backup
        pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                --schema-only --no-owner --no-privileges \
                > "$backup_dir/schema_backup.sql"
        
        # Create data backup
        pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                --data-only --no-owner --no-privileges \
                > "$backup_dir/data_backup.sql"
        
        # Create full backup
        pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                --no-owner --no-privileges \
                > "$backup_dir/full_backup.sql"
        
        # Compress backups
        gzip "$backup_dir"/*.sql
        
        print_status "Backup created at: $backup_dir ✓"
        echo "$backup_dir" > .last_backup_path
    fi
}

# Run Flyway migrations
run_migrations() {
    print_header "Running database migrations..."
    
    cd data-processing
    
    # Set Flyway properties
    export FLYWAY_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME"
    export FLYWAY_USER="$DB_USER"
    export FLYWAY_PASSWORD="$DB_PASSWORD"
    export FLYWAY_SCHEMAS="public"
    export FLYWAY_LOCATIONS="filesystem:src/main/resources/db/migration"
    
    case "$ACTION" in
        "migrate")
            print_status "Applying migrations..."
            mvn flyway:migrate -Dflyway.url="$FLYWAY_URL" \
                              -Dflyway.user="$FLYWAY_USER" \
                              -Dflyway.password="$FLYWAY_PASSWORD"
            ;;
        "info")
            print_status "Migration status:"
            mvn flyway:info -Dflyway.url="$FLYWAY_URL" \
                           -Dflyway.user="$FLYWAY_USER" \
                           -Dflyway.password="$FLYWAY_PASSWORD"
            ;;
        "validate")
            print_status "Validating migrations..."
            mvn flyway:validate -Dflyway.url="$FLYWAY_URL" \
                               -Dflyway.user="$FLYWAY_USER" \
                               -Dflyway.password="$FLYWAY_PASSWORD"
            ;;
        "repair")
            print_warning "Repairing migration metadata..."
            mvn flyway:repair -Dflyway.url="$FLYWAY_URL" \
                             -Dflyway.user="$FLYWAY_USER" \
                             -Dflyway.password="$FLYWAY_PASSWORD"
            ;;
        "rollback")
            if [ "$ENVIRONMENT" = "production" ]; then
                print_error "Rollback not allowed in production. Use manual restore from backup."
                exit 1
            fi
            
            print_warning "Rolling back last migration..."
            # Flyway doesn't support automatic rollback, so we'll restore from backup
            restore_from_backup
            return
            ;;
        *)
            print_error "Invalid action: $ACTION. Use 'migrate', 'info', 'validate', 'repair', or 'rollback'"
            exit 1
            ;;
    esac
    
    cd ..
    
    print_status "Migration action '$ACTION' completed ✓"
}

# Restore from backup
restore_from_backup() {
    if [ ! -f ".last_backup_path" ]; then
        print_error "No backup path found. Cannot restore."
        exit 1
    fi
    
    backup_path=$(cat .last_backup_path)
    
    if [ ! -d "$backup_path" ]; then
        print_error "Backup directory not found: $backup_path"
        exit 1
    fi
    
    print_warning "Restoring from backup: $backup_path"
    
    export PGPASSWORD="$DB_PASSWORD"
    
    # Drop and recreate database (be very careful!)
    if [ "$ENVIRONMENT" != "production" ]; then
        print_warning "Dropping and recreating database..."
        
        # Terminate active connections
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "
            SELECT pg_terminate_backend(pid) 
            FROM pg_stat_activity 
            WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();"
        
        # Drop and recreate database
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "DROP DATABASE IF EXISTS $DB_NAME;"
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "CREATE DATABASE $DB_NAME;"
        
        # Restore from backup
        gunzip -c "$backup_path/full_backup.sql.gz" | \
            psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"
        
        print_status "Database restored from backup ✓"
    else
        print_error "Database restore in production requires manual intervention for safety."
        exit 1
    fi
}

# Verify migration
verify_migration() {
    print_header "Verifying migration..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    # Check if TimescaleDB extension is available
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT * FROM pg_extension WHERE extname = 'timescaledb';" | grep -q timescaledb; then
        print_status "TimescaleDB extension verified ✓"
    else
        print_warning "TimescaleDB extension not found"
    fi
    
    # Check if PostGIS extension is available
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT * FROM pg_extension WHERE extname = 'postgis';" | grep -q postgis; then
        print_status "PostGIS extension verified ✓"
    else
        print_warning "PostGIS extension not found"
    fi
    
    # Check core tables
    tables=("collision_events" "analysis_jobs" "particle_tracks" "detector_hits")
    
    for table in "${tables[@]}"; do
        if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "\dt $table" | grep -q "$table"; then
            print_status "Table $table exists ✓"
        else
            print_warning "Table $table not found"
        fi
    done
    
    print_status "Migration verification completed ✓"
}

# Display usage
usage() {
    echo "Usage: $0 <environment> <action>"
    echo ""
    echo "Environments:"
    echo "  local      - Local development database"
    echo "  staging    - Staging environment database"
    echo "  production - Production environment database"
    echo ""
    echo "Actions:"
    echo "  migrate    - Apply pending migrations (default)"
    echo "  info       - Show migration status"
    echo "  validate   - Validate migration files"
    echo "  repair     - Repair migration metadata"
    echo "  rollback   - Rollback to previous state (not available in production)"
    echo ""
    echo "Examples:"
    echo "  $0 local migrate"
    echo "  $0 staging info"
    echo "  $0 production validate"
}

# Main execution
main() {
    if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
        usage
        exit 0
    fi
    
    print_status "Starting database migration for $ENVIRONMENT environment..."
    
    check_prerequisites
    test_connection
    
    if [ "$ACTION" = "migrate" ]; then
        create_backup
    fi
    
    run_migrations
    
    if [ "$ACTION" = "migrate" ]; then
        verify_migration
    fi
    
    print_status "🎉 Database migration completed successfully!"
}

# Run main function
main