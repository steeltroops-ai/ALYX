#!/bin/bash

# ALYX Database Backup Script
# This script creates automated backups of the ALYX database

set -e

echo "💾 ALYX Database Backup Tool"

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
    echo -e "${BLUE}[BACKUP]${NC} $1"
}

# Configuration
ENVIRONMENT="${1:-local}"
BACKUP_TYPE="${2:-full}"
RETENTION_DAYS="${3:-30}"

# Backup directory
BACKUP_BASE_DIR="backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$BACKUP_BASE_DIR/$ENVIRONMENT/$TIMESTAMP"

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
    
    if ! command -v pg_dump &> /dev/null; then
        print_error "pg_dump is not installed. Please install postgresql-client first."
        exit 1
    fi
    
    if ! command -v gzip &> /dev/null; then
        print_error "gzip is not installed. Please install gzip first."
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
    
    if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --schema-only --no-owner --no-privileges -f /dev/null 2>/dev/null; then
        print_status "Database connection successful ✓"
    else
        print_error "Cannot connect to database. Please check connection parameters."
        exit 1
    fi
}

# Create backup directory
create_backup_dir() {
    print_status "Creating backup directory: $BACKUP_DIR"
    
    mkdir -p "$BACKUP_DIR"
    
    # Create metadata file
    cat > "$BACKUP_DIR/backup_info.txt" << EOF
ALYX Database Backup Information
================================
Environment: $ENVIRONMENT
Backup Type: $BACKUP_TYPE
Timestamp: $TIMESTAMP
Database Host: $DB_HOST
Database Port: $DB_PORT
Database Name: $DB_NAME
Database User: $DB_USER
Created By: $(whoami)
Created At: $(date)
EOF
    
    print_status "Backup directory created ✓"
}

# Create schema backup
backup_schema() {
    print_header "Creating schema backup..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
            --schema-only --no-owner --no-privileges \
            --verbose \
            > "$BACKUP_DIR/schema_backup.sql" 2>"$BACKUP_DIR/schema_backup.log"
    
    # Compress schema backup
    gzip "$BACKUP_DIR/schema_backup.sql"
    
    print_status "Schema backup completed ✓"
}

# Create data backup
backup_data() {
    print_header "Creating data backup..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    if [ "$BACKUP_TYPE" = "incremental" ]; then
        # For incremental backups, only backup data modified in the last 24 hours
        # This is a simplified approach - in production, you might use WAL-E or similar
        print_status "Creating incremental data backup (last 24 hours)..."
        
        pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                --data-only --no-owner --no-privileges \
                --where="created_at >= NOW() - INTERVAL '24 hours' OR updated_at >= NOW() - INTERVAL '24 hours'" \
                --verbose \
                > "$BACKUP_DIR/data_incremental_backup.sql" 2>"$BACKUP_DIR/data_backup.log"
        
        gzip "$BACKUP_DIR/data_incremental_backup.sql"
    else
        # Full data backup
        print_status "Creating full data backup..."
        
        pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                --data-only --no-owner --no-privileges \
                --verbose \
                > "$BACKUP_DIR/data_backup.sql" 2>"$BACKUP_DIR/data_backup.log"
        
        gzip "$BACKUP_DIR/data_backup.sql"
    fi
    
    print_status "Data backup completed ✓"
}

# Create full backup
backup_full() {
    print_header "Creating full database backup..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
            --no-owner --no-privileges \
            --verbose \
            > "$BACKUP_DIR/full_backup.sql" 2>"$BACKUP_DIR/full_backup.log"
    
    # Compress full backup
    gzip "$BACKUP_DIR/full_backup.sql"
    
    print_status "Full backup completed ✓"
}

# Create custom format backup (for faster restore)
backup_custom() {
    print_header "Creating custom format backup..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
            --format=custom --no-owner --no-privileges \
            --verbose \
            --file="$BACKUP_DIR/custom_backup.dump" 2>"$BACKUP_DIR/custom_backup.log"
    
    # Compress custom backup
    gzip "$BACKUP_DIR/custom_backup.dump"
    
    print_status "Custom format backup completed ✓"
}

# Create table-specific backups for large tables
backup_large_tables() {
    print_header "Creating table-specific backups for large tables..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    # List of large tables that need special handling
    large_tables=("collision_events" "particle_tracks" "detector_hits")
    
    for table in "${large_tables[@]}"; do
        print_status "Backing up table: $table"
        
        # Check if table exists
        if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "\dt $table" | grep -q "$table"; then
            pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                    --table="$table" --data-only --no-owner --no-privileges \
                    --verbose \
                    > "$BACKUP_DIR/table_${table}_backup.sql" 2>"$BACKUP_DIR/table_${table}_backup.log"
            
            gzip "$BACKUP_DIR/table_${table}_backup.sql"
            print_status "Table $table backup completed ✓"
        else
            print_warning "Table $table not found, skipping..."
        fi
    done
}

# Calculate backup size and create checksum
finalize_backup() {
    print_header "Finalizing backup..."
    
    # Calculate total backup size
    backup_size=$(du -sh "$BACKUP_DIR" | cut -f1)
    
    # Create checksums for all backup files
    cd "$BACKUP_DIR"
    find . -name "*.gz" -o -name "*.dump.gz" | xargs md5sum > checksums.md5
    cd - > /dev/null
    
    # Update backup info
    cat >> "$BACKUP_DIR/backup_info.txt" << EOF

Backup Statistics:
==================
Total Size: $backup_size
Files Created: $(find "$BACKUP_DIR" -name "*.gz" -o -name "*.dump.gz" | wc -l)
Checksum File: checksums.md5
Completed At: $(date)
EOF
    
    print_status "Backup size: $backup_size"
    print_status "Backup finalized ✓"
}

# Clean up old backups
cleanup_old_backups() {
    print_header "Cleaning up old backups..."
    
    if [ -d "$BACKUP_BASE_DIR/$ENVIRONMENT" ]; then
        # Find and remove backups older than retention period
        old_backups=$(find "$BACKUP_BASE_DIR/$ENVIRONMENT" -type d -name "20*" -mtime +$RETENTION_DAYS)
        
        if [ -n "$old_backups" ]; then
            echo "$old_backups" | while read -r old_backup; do
                print_status "Removing old backup: $old_backup"
                rm -rf "$old_backup"
            done
            
            removed_count=$(echo "$old_backups" | wc -l)
            print_status "Removed $removed_count old backup(s) ✓"
        else
            print_status "No old backups to remove ✓"
        fi
    fi
}

# Upload backup to cloud storage (optional)
upload_to_cloud() {
    if [ -n "$AWS_S3_BUCKET" ] && command -v aws &> /dev/null; then
        print_header "Uploading backup to S3..."
        
        aws s3 sync "$BACKUP_DIR" "s3://$AWS_S3_BUCKET/alyx-backups/$ENVIRONMENT/$TIMESTAMP/" \
            --storage-class STANDARD_IA
        
        print_status "Backup uploaded to S3 ✓"
    elif [ -n "$GCS_BUCKET" ] && command -v gsutil &> /dev/null; then
        print_header "Uploading backup to Google Cloud Storage..."
        
        gsutil -m cp -r "$BACKUP_DIR" "gs://$GCS_BUCKET/alyx-backups/$ENVIRONMENT/"
        
        print_status "Backup uploaded to GCS ✓"
    else
        print_status "No cloud storage configured, backup stored locally only"
    fi
}

# Display usage
usage() {
    echo "Usage: $0 <environment> <backup_type> [retention_days]"
    echo ""
    echo "Environments:"
    echo "  local      - Local development database"
    echo "  staging    - Staging environment database"
    echo "  production - Production environment database"
    echo ""
    echo "Backup Types:"
    echo "  full        - Complete database backup (default)"
    echo "  schema      - Schema only backup"
    echo "  data        - Data only backup"
    echo "  incremental - Incremental data backup (last 24 hours)"
    echo "  custom      - Custom format backup (faster restore)"
    echo ""
    echo "Retention Days:"
    echo "  Number of days to keep backups (default: 30)"
    echo ""
    echo "Examples:"
    echo "  $0 local full"
    echo "  $0 production full 90"
    echo "  $0 staging incremental 7"
}

# Main execution
main() {
    if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
        usage
        exit 0
    fi
    
    print_status "Starting database backup for $ENVIRONMENT environment..."
    print_status "Backup type: $BACKUP_TYPE"
    print_status "Retention period: $RETENTION_DAYS days"
    
    check_prerequisites
    test_connection
    create_backup_dir
    
    case "$BACKUP_TYPE" in
        "full")
            backup_schema
            backup_data
            backup_full
            backup_custom
            if [ "$ENVIRONMENT" = "production" ]; then
                backup_large_tables
            fi
            ;;
        "schema")
            backup_schema
            ;;
        "data")
            backup_data
            ;;
        "incremental")
            backup_data
            ;;
        "custom")
            backup_custom
            ;;
        *)
            print_error "Invalid backup type: $BACKUP_TYPE"
            usage
            exit 1
            ;;
    esac
    
    finalize_backup
    cleanup_old_backups
    upload_to_cloud
    
    print_status "🎉 Database backup completed successfully!"
    print_status "Backup location: $BACKUP_DIR"
}

# Run main function
main