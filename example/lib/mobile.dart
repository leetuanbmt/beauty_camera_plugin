import 'package:flutter/material.dart';

class IDCaptureGuidancePage extends StatelessWidget {
  const IDCaptureGuidancePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          'Lưu ý khi chụp giấy tờ tùy thân',
          style: TextStyle(
            color: Color(0xFFE19415),
            fontSize: 17,
            fontFamily: 'Roboto',
          ),
        ),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            const SizedBox(height: 20),
            // Step indicator
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _buildStepCircle(1, true),
                _buildStepLine(true),
                _buildStepCircle(2, false),
                _buildStepLine(false),
                _buildStepCircle(3, false),
              ],
            ),
            const SizedBox(height: 16),
            const Text(
              'Bước 1: chụp ảnh CMND/CCCD',
              style: TextStyle(
                fontSize: 18,
                fontFamily: 'Roboto',
                color: Colors.black,
              ),
            ),
            const SizedBox(height: 24),
            // Guidance cards
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                children: [
                  _buildGuidanceCard(
                    'Sử dụng giấy tờ còn hạn sử dụng',
                    'assets/images/valid_id.png',
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    'Đảm bảo ảnh rõ nét, không bị mờ lóa',
                    'assets/images/clear_photo.png',
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    'Đặt giấy tờ trên mặt phẳng và chụp thẳng góc',
                    'assets/images/flat_surface.png',
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    'Không cầm trên tay',
                    'assets/images/no_hand.png',
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    'Không sử dụng bản scan, photocoppy',
                    'assets/images/no_copy.png',
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    'Không dùng giấy tờ bị cắt góc, quăn mép, bị nhòe',
                    'assets/images/no_damage.png',
                  ),
                ],
              ),
            ),
            const SizedBox(height: 32),
            // Continue button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: ElevatedButton(
                onPressed: () {
                  // Handle continue button press
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFE7A232),
                  minimumSize: const Size(double.infinity, 48),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(6),
                  ),
                ),
                child: const Text(
                  'TIẾP TỤC',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    fontFamily: 'Roboto',
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildStepCircle(int number, bool isActive) {
    return Container(
      width: 32,
      height: 32,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: isActive ? const Color(0xFFE7A232) : const Color(0xFFDCDCDC),
      ),
      child: Center(
        child: Text(
          number.toString(),
          style: TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
            fontFamily: 'Roboto',
          ),
        ),
      ),
    );
  }

  Widget _buildStepLine(bool isActive) {
    return Container(
      width: 60,
      height: 2,
      color: isActive ? const Color(0xFFE7A232) : const Color(0xFFDCD8D8),
    );
  }

  Widget _buildGuidanceCard(String text, String imagePath) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.1),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Image.asset(
            imagePath,
            width: 48,
            height: 48,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                fontSize: 14,
                color: Color(0xB3000000),
                fontFamily: 'Roboto',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
