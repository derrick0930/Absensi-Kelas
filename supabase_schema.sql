-- 1. TABEL PENGGUNA (USERS)
-- Menyimpan kredensial Siswa, Guru, dan Admin (termasuk NISN dan Username unik)
CREATE TABLE IF NOT EXISTS public.users (
    id BIGSERIAL PRIMARY KEY,           -- Nomor urut database otomatis (1, 2, 3, dst.)
    nisn TEXT UNIQUE,                   -- NISN untuk Siswa (atau NIP untuk Guru)
    username TEXT UNIQUE,               -- Username unik untuk Siswa / Guru / Admin
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    password TEXT NOT NULL,
    biometric_enrolled BOOLEAN DEFAULT FALSE,
    face_enrolled BOOLEAN DEFAULT FALSE,
    face_signature TEXT,
    biometric_enrolled_at BIGINT,
    enrolled_device_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Migration safety jika tabel users sudah ada sebelumnya di Supabase
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS username TEXT UNIQUE;

-- 2. TABEL PROFIL SISWA (STUDENTS)
CREATE TABLE IF NOT EXISTS public.students (
    nisn TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    birth_place_date TEXT,
    address TEXT,
    religion TEXT,
    blood_type TEXT,
    school_name TEXT DEFAULT 'SMKS TARUNA JAYA PRAWIRA TUBAN',
    class_name TEXT DEFAULT 'XI TKJ 1',
    headmaster_name TEXT DEFAULT 'Bambang Kusdiyanto, ST.M.Pd',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. TABEL GURU (TEACHERS)
CREATE TABLE IF NOT EXISTS public.teachers (
    nip TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    subject TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. TABEL KELAS (CLASSES) - Memisahkan data kelas & kordinat GPS ruang kelas
CREATE TABLE IF NOT EXISTS public.classes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,          -- Contoh: 'XI IPA 2', 'XI TKJ 1'
    room TEXT DEFAULT 'R. 12',          -- Ruang kelas: 'R. 12', 'Lab Komputer 1'
    latitude NUMERIC(10, 7) NOT NULL,   -- Latitude kordinat kelas
    longitude NUMERIC(10, 7) NOT NULL,  -- Longitude kordinat kelas
    radius_meters INT DEFAULT 50,       -- Radius toleransi presensi dalam meter (misal 50 meter)
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed Kelas Default
INSERT INTO public.classes (id, name, room, latitude, longitude, radius_meters)
VALUES 
    ('cls_xi_ipa_2', 'XI IPA 2', 'R. 12', -6.899720, 112.049440, 50),
    ('cls_xi_tkj_1', 'XI TKJ 1', 'Lab Komputer 1', -6.899650, 112.049520, 50),
    ('cls_xi_tkj_2', 'XI TKJ 2', 'Lab Komputer 2', -6.899580, 112.049600, 50),
    ('cls_xii_rpl_1', 'XII RPL 1', 'Lab Software 3', -6.899500, 112.049700, 50)
ON CONFLICT (id) DO NOTHING;

-- 5. TABEL JADWAL MENGAJAR & PRESENSI (SCHEDULES)
CREATE TABLE IF NOT EXISTS public.schedules (
    id TEXT PRIMARY KEY,
    class_name TEXT NOT NULL,
    subject TEXT NOT NULL,
    teacher_id TEXT NOT NULL,
    teacher_name TEXT NOT NULL,
    period_start INT NOT NULL,
    period_end INT NOT NULL,
    time_slot TEXT NOT NULL,
    room TEXT NOT NULL,
    day_name TEXT DEFAULT 'Senin',
    session_date TEXT DEFAULT '',
    is_open BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. TABEL REKAP PRESENSI (ATTENDANCE)
-- Menyimpan kehadiran siswa beserta bukti verifikasi 3-lapis (GPS Kelas, Biometrik, Wajah)
CREATE TABLE IF NOT EXISTS public.attendance (
    id TEXT PRIMARY KEY,
    schedule_id TEXT NOT NULL,
    student_nisn TEXT NOT NULL,
    student_name TEXT NOT NULL,
    class_name TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'Hadir',
    biometric_verified BOOLEAN DEFAULT TRUE,
    face_verified BOOLEAN DEFAULT TRUE,
    location_distance_meters INT DEFAULT 0,
    verification_method TEXT DEFAULT '3-Lapis (GPS Kelas + Biometrik + Wajah)',
    remarks TEXT DEFAULT 'Presensi Mandiri Terverifikasi',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- =========================================================================
-- AKTIFKAN AKSES PUBLIK (ROW LEVEL SECURITY POLICIES)
-- =========================================================================
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read classes" ON public.classes FOR SELECT USING (true);
CREATE POLICY "Allow public insert classes" ON public.classes FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update classes" ON public.classes FOR UPDATE USING (true);

CREATE POLICY "Allow public read users" ON public.users FOR SELECT USING (true);
CREATE POLICY "Allow public insert users" ON public.users FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update users" ON public.users FOR UPDATE USING (true);

CREATE POLICY "Allow public read students" ON public.students FOR SELECT USING (true);
CREATE POLICY "Allow public insert students" ON public.students FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update students" ON public.students FOR UPDATE USING (true);

CREATE POLICY "Allow public read teachers" ON public.teachers FOR SELECT USING (true);
CREATE POLICY "Allow public read schedules" ON public.schedules FOR SELECT USING (true);
CREATE POLICY "Allow public insert schedules" ON public.schedules FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update schedules" ON public.schedules FOR UPDATE USING (true);
CREATE POLICY "Allow public delete schedules" ON public.schedules FOR DELETE USING (true);

CREATE POLICY "Allow public read attendance" ON public.attendance FOR SELECT USING (true);
CREATE POLICY "Allow public insert attendance" ON public.attendance FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public delete attendance" ON public.attendance FOR DELETE USING (true);

-- 7. TABEL SURAT IZIN SISWA (PERMISSIONS)
CREATE TABLE IF NOT EXISTS public.permissions (
    id TEXT PRIMARY KEY,
    student_nisn TEXT NOT NULL,
    student_name TEXT NOT NULL,
    class_name TEXT NOT NULL,
    permission_type TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    total_days INT DEFAULT 1,
    reason TEXT NOT NULL,
    photo_base64 TEXT,
    status TEXT DEFAULT 'Menunggu',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.permissions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow public read permissions" ON public.permissions FOR SELECT USING (true);
CREATE POLICY "Allow public insert permissions" ON public.permissions FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update permissions" ON public.permissions FOR UPDATE USING (true);

